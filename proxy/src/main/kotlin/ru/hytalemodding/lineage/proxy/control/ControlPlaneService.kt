/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.control

import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.event.security.TokenValidationEvent
import ru.hytalemodding.lineage.api.event.security.TokenValidationReason as ApiTokenValidationReason
import ru.hytalemodding.lineage.api.event.security.TokenValidationResult as ApiTokenValidationResult
import ru.hytalemodding.lineage.api.event.player.PlayerAuthenticatedEvent
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.player.PlayerState
import ru.hytalemodding.lineage.proxy.config.MessagingConfig
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl
import ru.hytalemodding.lineage.proxy.util.Logging
import ru.hytalemodding.lineage.shared.control.ControlChannels
import ru.hytalemodding.lineage.shared.control.ControlEnvelope
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlPayloadCodec
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import ru.hytalemodding.lineage.shared.control.ControlReplayProtector
import ru.hytalemodding.lineage.shared.control.TokenValidationReason
import ru.hytalemodding.lineage.shared.control.TokenValidationResult
import ru.hytalemodding.lineage.shared.control.TransferRequest
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import java.security.SecureRandom

class ControlPlaneService(
    messaging: Messaging,
    private val config: MessagingConfig,
    private val eventBus: EventBus,
    private val players: PlayerManagerImpl,
    private val clock: Clock = SystemClock,
) : TransferRequestSender {
    private val logger = Logging.logger(ControlPlaneService::class.java)
    private val replayProtector = ControlReplayProtector(
        config.controlReplayWindowMillis,
        config.controlReplayMaxEntries,
        clock,
    )
    private val random = SecureRandom()
    private val channel = messaging.registerChannel(
        ControlChannels.CONTROL_CHANNEL_ID,
        MessageHandler { message -> handleMessage(message.payload) }
    )

    override fun sendTransferRequest(request: TransferRequest): Boolean {
        val payload = ControlPayloadCodec.encodeTransferRequest(request)
        if (payload.size > config.controlMaxPayload) {
            return false
        }
        val envelope = buildEnvelope(ControlMessageType.TRANSFER_REQUEST, payload)
        channel.send(ControlProtocol.encode(envelope))
        return true
    }

    private fun handleMessage(payload: ByteArray) {
        if (payload.size > config.controlMaxPayload) {
            return
        }
        val envelope = ControlProtocol.decode(payload) ?: return
        if (!ControlProtocol.isTimestampValid(
                envelope.issuedAtMillis,
                envelope.ttlMillis,
                clock.nowMillis(),
                config.controlMaxSkewMillis,
            )
        ) {
            return
        }
        if (envelope.payload.size > config.controlMaxPayload) {
            return
        }
        if (!replayProtector.tryRegister(envelope.senderId, envelope.type, envelope.nonce)) {
            return
        }
        when (envelope.type) {
            ControlMessageType.TRANSFER_RESULT -> handleTransferResult(envelope)
            ControlMessageType.TOKEN_VALIDATION -> handleTokenValidation(envelope)
            else -> Unit
        }
    }

    private fun handleTransferResult(envelope: ControlEnvelope) {
        val result = ControlPayloadCodec.decodeTransferResult(envelope.payload) ?: return
        logger.info(
            "Control-plane transfer result: correlationId={}, status={}, reason={}",
            result.correlationId,
            result.status,
            result.reason,
        )
    }

    private fun handleTokenValidation(envelope: ControlEnvelope) {
        val notice = ControlPayloadCodec.decodeTokenValidationNotice(envelope.payload) ?: return
        val apiResult = when (notice.result) {
            TokenValidationResult.ACCEPTED -> ApiTokenValidationResult.ACCEPTED
            TokenValidationResult.REJECTED -> ApiTokenValidationResult.REJECTED
        }
        val apiReason = notice.reason?.let { mapTokenReason(it) }
        eventBus.post(
            TokenValidationEvent(
                playerId = notice.playerId,
                backendId = notice.backendId,
                result = apiResult,
                reason = apiReason,
            )
        )
        if (notice.result == TokenValidationResult.ACCEPTED) {
            val player = players.get(notice.playerId) as? ProxyPlayerImpl ?: return
            if (player.state != PlayerState.AUTHENTICATED) {
                player.state = PlayerState.AUTHENTICATED
            }
            eventBus.post(PlayerAuthenticatedEvent(player, notice.backendId))
        }
    }

    private fun buildEnvelope(type: ControlMessageType, payload: ByteArray): ControlEnvelope {
        return ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = type,
            senderId = config.controlSenderId,
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonce = nextNonce(),
            payload = payload,
        )
    }

    private fun nextNonce(): ByteArray {
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE)
        random.nextBytes(nonce)
        return nonce
    }

    private fun mapTokenReason(reason: TokenValidationReason): ApiTokenValidationReason {
        return when (reason) {
            TokenValidationReason.MALFORMED -> ApiTokenValidationReason.MALFORMED
            TokenValidationReason.INVALID_SIGNATURE -> ApiTokenValidationReason.INVALID_SIGNATURE
            TokenValidationReason.EXPIRED -> ApiTokenValidationReason.EXPIRED
            TokenValidationReason.NOT_YET_VALID -> ApiTokenValidationReason.NOT_YET_VALID
            TokenValidationReason.UNSUPPORTED_VERSION -> ApiTokenValidationReason.UNSUPPORTED_VERSION
            TokenValidationReason.TARGET_MISMATCH -> ApiTokenValidationReason.TARGET_MISMATCH
            TokenValidationReason.REPLAYED -> ApiTokenValidationReason.REPLAYED
        }
    }
}
