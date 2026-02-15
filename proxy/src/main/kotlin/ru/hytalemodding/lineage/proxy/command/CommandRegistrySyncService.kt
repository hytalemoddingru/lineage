/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.CommandFlag
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.proxy.util.Logging
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import ru.hytalemodding.lineage.shared.control.ControlReplayProtector
import ru.hytalemodding.lineage.shared.command.ProxyCommandDescriptor
import ru.hytalemodding.lineage.shared.command.ProxyCommandFlags
import ru.hytalemodding.lineage.shared.command.ProxyCommandRegistryProtocol
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

class CommandRegistrySyncService(
    private val registry: CommandRegistryImpl,
    private val messaging: Messaging,
    private val clock: Clock = SystemClock,
) {
    private val logger = Logging.logger(CommandRegistrySyncService::class.java)
    private val rejectCounters = ConcurrentHashMap<RejectReason, LongAdder>()
    private val replayProtector = ControlReplayProtector(
        windowMillis = 10_000L,
        maxEntries = 100_000,
        clock = clock,
    )
    private val outputChannel = messaging.channel(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID)
        ?: messaging.registerChannel(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID, MessageHandler { })

    init {
        messaging.registerChannel(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, MessageHandler { message ->
            val version = ProxyCommandRegistryProtocol.peekVersion(message.payload)
            if (version == null) {
                reject(RejectReason.MALFORMED_REQUEST, "missing protocol version")
                return@MessageHandler
            }
            if (!ProxyCommandRegistryProtocol.hasSupportedVersion(message.payload)) {
                reject(RejectReason.VERSION_MISMATCH, "unsupported version=$version")
                return@MessageHandler
            }
            val request = ProxyCommandRegistryProtocol.decodeRequest(message.payload) ?: run {
                reject(RejectReason.MALFORMED_REQUEST, "payload decode failed")
                return@MessageHandler
            }
            val correlationId = registryRequestCorrelationId(request.senderId, request.nonce)
            if (request.senderId != EXPECTED_REQUEST_SENDER_ID) {
                reject(RejectReason.UNEXPECTED_SENDER, "sender=${request.senderId}", correlationId)
                return@MessageHandler
            }
            if (!ControlProtocol.isTimestampValid(
                    request.issuedAtMillis,
                    request.ttlMillis,
                    clock.nowMillis(),
                )
            ) {
                reject(RejectReason.INVALID_TIMESTAMP, "timestamp window validation failed", correlationId)
                return@MessageHandler
            }
            if (!replayProtector.tryRegister(request.senderId, ControlMessageType.TRANSFER_REQUEST, request.nonce)) {
                reject(RejectReason.REPLAYED_REQUEST, "replayed request nonce", correlationId)
                return@MessageHandler
            }
            sendSnapshot()
        })
        registry.addListener { sendSnapshot() }
    }

    internal fun rejectCountersSnapshot(): Map<String, Long> {
        return rejectCounters.entries.associate { it.key.name to it.value.sum() }
    }

    fun sendSnapshot() {
        val descriptors = registry.snapshot()
            .sortedWith(compareBy<CommandEntry>({ it.ownerId }, { it.baseNames.firstOrNull() ?: "" }))
            .map { entry -> entry.toDescriptor() }
        val payload = runCatching { ProxyCommandRegistryProtocol.encodeSnapshot(descriptors) }
            .getOrElse { ex ->
                reject(RejectReason.OUTBOUND_PAYLOAD_TOO_LARGE, "snapshot encode failed: ${ex.message}")
                return
            }
        if (payload.size > MAX_PAYLOAD_BYTES) {
            reject(RejectReason.OUTBOUND_PAYLOAD_TOO_LARGE, "payload size=${payload.size} exceeds $MAX_PAYLOAD_BYTES")
            return
        }
        outputChannel.send(payload)
    }

    private fun reject(reason: RejectReason, details: String, correlationId: String? = null) {
        rejectCounters.computeIfAbsent(reason) { LongAdder() }.increment()
        logger.warn(
            "{}",
            StructuredLog.event(
                category = "command-registry-sync",
                severity = "WARN",
                reason = reason.name,
                correlationId = correlationId,
                fields = mapOf("details" to details),
            )
        )
    }

    private fun CommandEntry.toDescriptor(): ProxyCommandDescriptor {
        return ProxyCommandDescriptor(
            namespace = ownerId,
            name = baseNames.first(),
            aliases = baseNames.drop(1),
            description = command.description,
            usage = command.usage,
            permission = command.permission,
            flags = encodeFlags(command.flags),
        )
    }

    private fun encodeFlags(flags: Set<CommandFlag>): Int {
        var mask = 0
        if (CommandFlag.PLAYER_ONLY in flags) {
            mask = mask or ProxyCommandFlags.PLAYER_ONLY
        }
        if (CommandFlag.HIDDEN in flags) {
            mask = mask or ProxyCommandFlags.HIDDEN
        }
        return mask
    }

    private fun registryRequestCorrelationId(senderId: String, nonce: ByteArray): String {
        val nonceB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
        return "$senderId:$nonceB64"
    }

    private companion object {
        private const val MAX_PAYLOAD_BYTES = 60_000
        private const val EXPECTED_REQUEST_SENDER_ID = "backend"
    }

    private enum class RejectReason {
        MALFORMED_REQUEST,
        VERSION_MISMATCH,
        UNEXPECTED_SENDER,
        INVALID_TIMESTAMP,
        REPLAYED_REQUEST,
        OUTBOUND_PAYLOAD_TOO_LARGE,
    }
}
