/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.control

import org.slf4j.LoggerFactory
import ru.hytalemodding.lineage.backend.config.BackendConfig
import ru.hytalemodding.lineage.backend.messaging.BackendMessaging
import ru.hytalemodding.lineage.shared.control.ControlChannels
import ru.hytalemodding.lineage.shared.control.ControlEnvelope
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlPayloadCodec
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import ru.hytalemodding.lineage.shared.control.ControlReplayProtector
import ru.hytalemodding.lineage.shared.control.TokenValidationNotice
import ru.hytalemodding.lineage.shared.control.TokenValidationReason
import ru.hytalemodding.lineage.shared.control.TokenValidationResult
import ru.hytalemodding.lineage.shared.control.TransferFailureReason
import ru.hytalemodding.lineage.shared.control.TransferRequest
import ru.hytalemodding.lineage.shared.control.TransferResult
import ru.hytalemodding.lineage.shared.control.TransferResultStatus
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import com.hypixel.hytale.server.core.universe.Universe
import java.security.SecureRandom
import java.util.UUID

class BackendControlPlaneService(
    private val config: BackendConfig,
    private val clock: Clock = SystemClock,
) {
    private val logger = LoggerFactory.getLogger(BackendControlPlaneService::class.java)
    private val replayProtector = ControlReplayProtector(
        config.controlReplayWindowMillis,
        config.controlReplayMaxEntries,
        clock,
    )
    private val random = SecureRandom()

    fun start() {
        BackendMessaging.registerChannel(ControlChannels.CONTROL_CHANNEL_ID, this::handleMessage)
    }

    fun stop() {
        BackendMessaging.unregisterChannel(ControlChannels.CONTROL_CHANNEL_ID)
    }

    fun sendTokenValidationNotice(
        playerId: UUID,
        backendId: String,
        result: TokenValidationResult,
        reason: TokenValidationReason?,
    ) {
        val notice = TokenValidationNotice(
            playerId = playerId,
            backendId = backendId,
            result = result,
            reason = reason,
        )
        sendEnvelope(ControlMessageType.TOKEN_VALIDATION, ControlPayloadCodec.encodeTokenValidationNotice(notice))
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
            ControlMessageType.TRANSFER_REQUEST -> handleTransferRequest(envelope)
            else -> Unit
        }
    }

    private fun handleTransferRequest(envelope: ControlEnvelope) {
        val request = ControlPayloadCodec.decodeTransferRequest(envelope.payload) ?: return
        val playerRef = Universe.get().getPlayer(request.playerId) ?: return
        try {
            playerRef.referToServer(config.proxyConnectHost, config.proxyConnectPort, request.referralData)
            sendTransferResult(request.correlationId, TransferResultStatus.OK, null)
        } catch (ex: Exception) {
            logger.warn("Failed to refer player {} via control-plane", request.playerId, ex)
            sendTransferResult(request.correlationId, TransferResultStatus.FAILED, TransferFailureReason.INTERNAL_ERROR)
        }
    }

    private fun sendTransferResult(
        correlationId: UUID,
        status: TransferResultStatus,
        reason: TransferFailureReason?,
    ) {
        val result = TransferResult(correlationId, status, reason)
        sendEnvelope(ControlMessageType.TRANSFER_RESULT, ControlPayloadCodec.encodeTransferResult(result))
    }

    private fun sendEnvelope(type: ControlMessageType, payload: ByteArray) {
        if (payload.size > config.controlMaxPayload) {
            return
        }
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = type,
            senderId = config.controlSenderId,
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonce = nextNonce(),
            payload = payload,
        )
        val encoded = ControlProtocol.encode(envelope)
        BackendMessaging.send(ControlChannels.CONTROL_CHANNEL_ID, encoded)
    }

    private fun nextNonce(): ByteArray {
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE)
        random.nextBytes(nonce)
        return nonce
    }
}
