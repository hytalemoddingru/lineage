/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.messaging.Message
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.api.player.PlayerState
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl
import ru.hytalemodding.lineage.proxy.text.RenderLimits
import ru.hytalemodding.lineage.proxy.util.Logging
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import ru.hytalemodding.lineage.shared.control.ControlReplayProtector
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/**
 * Bridges player command requests from the backend to the proxy command dispatcher.
 */
class PlayerCommandGateway(
    messaging: Messaging,
    private val dispatcher: CommandDispatcher,
    private val players: PlayerManager,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
    private val replayWindowMillis: Long = 10_000L,
    private val replayMaxEntries: Int = 100_000,
    private val maxSkewMillis: Long = ControlProtocol.MAX_SKEW_MILLIS,
    private val clock: Clock = SystemClock,
    private val renderLimitsProvider: () -> RenderLimits = { RenderLimits() },
) {
    private val logger = Logging.logger(PlayerCommandGateway::class.java)
    private val rejectCounters = ConcurrentHashMap<RejectReason, LongAdder>()
    private val replayProtector = ControlReplayProtector(
        windowMillis = replayWindowMillis,
        maxEntries = replayMaxEntries,
        clock = clock,
    )
    private val responseChannel = messaging.channel(PlayerCommandProtocol.RESPONSE_CHANNEL_ID)
        ?: messaging.registerChannel(PlayerCommandProtocol.RESPONSE_CHANNEL_ID, MessageHandler { })
    private val responder = CommandResponder(responseChannel)

    init {
        messaging.registerChannel(PlayerCommandProtocol.REQUEST_CHANNEL_ID, MessageHandler { message ->
            handleRequest(message)
        })
    }

    internal fun rejectCountersSnapshot(): Map<String, Long> {
        return rejectCounters.entries.associate { it.key.name to it.value.sum() }
    }

    private fun handleRequest(message: Message) {
        val version = PlayerCommandProtocol.peekVersion(message.payload)
        if (version == null) {
            reject(RejectReason.MALFORMED_REQUEST, "missing protocol version")
            return
        }
        if (!PlayerCommandProtocol.hasSupportedVersion(message.payload)) {
            reject(RejectReason.VERSION_MISMATCH, "unsupported version=$version")
            return
        }
        val request = PlayerCommandProtocol.decodeRequest(message.payload) ?: run {
            reject(RejectReason.MALFORMED_REQUEST, "payload decode failed")
            return
        }
        if (!ControlProtocol.isTimestampValid(
                request.issuedAtMillis,
                request.ttlMillis,
                clock.nowMillis(),
                maxSkewMillis,
            )
        ) {
            reject(RejectReason.INVALID_TIMESTAMP, "timestamp window validation failed", request.playerId.toString())
            return
        }
        if (!replayProtector.tryRegister("backend", ControlMessageType.TRANSFER_REQUEST, request.nonce)) {
            reject(RejectReason.REPLAYED_REQUEST, "replayed request nonce", request.playerId.toString())
            return
        }
        val player = players.get(request.playerId)
        if (player == null) {
            responder.send(request.playerId, messages.text(null, "gateway_player_not_found"))
            return
        }
        val language = (player as? ProxyPlayerImpl)?.language
        if (player.state != PlayerState.AUTHENTICATED && player.state != PlayerState.PLAYING) {
            responder.send(request.playerId, messages.text(language, "gateway_player_not_authenticated"))
            return
        }
        val sender: CommandSender = ProxyPlayerCommandSender(player, responder, messages, renderLimitsProvider)
        val normalized = normalizeInput(request.command)
        if (normalized.isEmpty()) {
            sender.sendMessage(messages.text(language, "gateway_command_empty"))
            return
        }
        val handled = dispatcher.dispatch(sender, normalized)
        if (!handled) {
            sender.sendMessage(messages.text(language, "gateway_unknown_command"))
        }
    }

    private fun reject(reason: RejectReason, details: String, correlationId: String? = null) {
        rejectCounters.computeIfAbsent(reason) { LongAdder() }.increment()
        logger.warn(
            "{}",
            StructuredLog.event(
                category = "command-gateway",
                severity = "WARN",
                reason = reason.name,
                correlationId = correlationId,
                fields = mapOf("details" to details),
            )
        )
    }

    private fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("/")) {
            return trimmed.removePrefix("/").trim()
        }
        return trimmed
    }

    private enum class RejectReason {
        MALFORMED_REQUEST,
        VERSION_MISMATCH,
        INVALID_TIMESTAMP,
        REPLAYED_REQUEST,
    }
}
