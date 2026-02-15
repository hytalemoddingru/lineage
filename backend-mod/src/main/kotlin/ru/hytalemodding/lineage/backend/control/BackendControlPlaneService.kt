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
import ru.hytalemodding.lineage.shared.control.BackendStatusNotice
import ru.hytalemodding.lineage.shared.control.TokenValidationNotice
import ru.hytalemodding.lineage.shared.control.TokenValidationReason
import ru.hytalemodding.lineage.shared.control.TokenValidationResult
import ru.hytalemodding.lineage.shared.control.TransferFailureReason
import ru.hytalemodding.lineage.shared.control.TransferRequest
import ru.hytalemodding.lineage.shared.control.TransferResult
import ru.hytalemodding.lineage.shared.control.TransferResultStatus
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import com.hypixel.hytale.server.core.universe.Universe
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BackendControlPlaneService(
    private val config: BackendConfig,
    private val clock: Clock = SystemClock,
    private val inboundProcessingHook: (() -> Unit)? = null,
) {
    private val logger = LoggerFactory.getLogger(BackendControlPlaneService::class.java)
    private val rejectCounters = ConcurrentHashMap<RejectReason, LongAdder>()
    private val replayProtector = ControlReplayProtector(
        config.controlReplayWindowMillis,
        config.controlReplayMaxEntries,
        clock,
    )
    @Volatile
    private var statusScheduler: ScheduledExecutorService? = null
    private val inboundInFlight = AtomicInteger(0)
    private val random = SecureRandom()
    private val started = AtomicBoolean(false)
    private val lastAnnouncedStatus = AtomicReference<Boolean?>(null)

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        BackendMessaging.registerChannel(ControlChannels.CONTROL_CHANNEL_ID, this::handleMessage)
        announceOnline()
        val scheduler = Executors.newSingleThreadScheduledExecutor().also { statusScheduler = it }
        scheduler.scheduleAtFixedRate(
            {
                runCatching { announceOnline() }
                    .onFailure { logger.warn("Failed to send backend status heartbeat", it) }
            },
            STATUS_HEARTBEAT_SECONDS,
            STATUS_HEARTBEAT_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) {
            return
        }
        repeat(OFFLINE_BURST_COUNT) { attempt ->
            announceOffline()
            if (attempt + 1 < OFFLINE_BURST_COUNT) {
                runCatching { Thread.sleep(OFFLINE_BURST_DELAY_MILLIS) }
            }
        }
        statusScheduler?.shutdownNow()
        statusScheduler = null
        BackendMessaging.unregisterChannel(ControlChannels.CONTROL_CHANNEL_ID)
    }

    fun announceOnline() {
        sendBackendStatus(online = true)
    }

    fun announceOffline() {
        sendBackendStatus(online = false)
    }

    internal fun rejectCountersSnapshot(): Map<String, Long> {
        return rejectCounters.entries.associate { it.key.name to it.value.sum() }
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
            reject(RejectReason.PACKET_TOO_LARGE, "packet size ${payload.size} exceeds max ${config.controlMaxPayload}")
            return
        }
        if (!tryAcquireInboundSlot()) {
            reject(
                RejectReason.INGRESS_BACKPRESSURE,
                "inbound control-plane limit reached (${config.controlMaxInflight} in-flight)",
            )
            return
        }
        try {
            inboundProcessingHook?.invoke()
            val envelope = ControlProtocol.decode(payload) ?: run {
                reject(RejectReason.MALFORMED_ENVELOPE, "failed to decode control envelope")
                return
            }
            val correlationId = envelopeCorrelationId(envelope)
            if (envelope.senderId != config.controlExpectedSenderId) {
                reject(
                    RejectReason.UNEXPECTED_SENDER,
                    "unexpected sender ${envelope.senderId}; expected ${config.controlExpectedSenderId}",
                    correlationId,
                )
                return
            }
            if (!ControlProtocol.isTimestampValid(
                    envelope.issuedAtMillis,
                    envelope.ttlMillis,
                    clock.nowMillis(),
                    config.controlMaxSkewMillis,
                )
            ) {
                reject(RejectReason.INVALID_TIMESTAMP, "timestamp window validation failed", correlationId)
                return
            }
            if (envelope.payload.size > config.controlMaxPayload) {
                reject(RejectReason.PAYLOAD_TOO_LARGE, "envelope payload too large: ${envelope.payload.size}", correlationId)
                return
            }
            if (!replayProtector.tryRegister(envelope.senderId, envelope.type, envelope.nonce)) {
                reject(
                    RejectReason.REPLAYED,
                    "replayed envelope from sender=${envelope.senderId} type=${envelope.type}",
                    correlationId,
                )
                return
            }
            when (envelope.type) {
                ControlMessageType.TRANSFER_REQUEST -> handleTransferRequest(envelope)
                else -> reject(RejectReason.UNSUPPORTED_TYPE, "unsupported message type ${envelope.type}", correlationId)
            }
        } finally {
            inboundInFlight.decrementAndGet()
        }
    }

    private fun tryAcquireInboundSlot(): Boolean {
        while (true) {
            val current = inboundInFlight.get()
            if (current >= config.controlMaxInflight) {
                return false
            }
            if (inboundInFlight.compareAndSet(current, current + 1)) {
                return true
            }
        }
    }

    private fun handleTransferRequest(envelope: ControlEnvelope) {
        val request = ControlPayloadCodec.decodeTransferRequest(envelope.payload) ?: run {
            reject(
                RejectReason.MALFORMED_PAYLOAD,
                "failed to decode TRANSFER_REQUEST payload",
                envelopeCorrelationId(envelope),
            )
            return
        }
        val playerRef = Universe.get().getPlayer(request.playerId) ?: return
        try {
            playerRef.referToServer(config.proxyConnectHost, config.proxyConnectPort, request.referralData)
            sendTransferResult(request.correlationId, TransferResultStatus.OK, null)
        } catch (ex: Exception) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "control-plane",
                    severity = "WARN",
                    reason = "TRANSFER_FORWARD_FAILED",
                    correlationId = request.correlationId.toString(),
                    fields = mapOf(
                        "playerId" to request.playerId,
                        "backendId" to request.targetBackendId,
                        "errorType" to ex.javaClass.simpleName,
                    ),
                ),
                ex,
            )
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
            reject(RejectReason.OUTBOUND_PAYLOAD_TOO_LARGE, "outbound payload too large for type=$type")
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

    private fun sendBackendStatus(online: Boolean) {
        val notice = BackendStatusNotice(
            backendId = config.serverId,
            online = online,
        )
        val previous = lastAnnouncedStatus.getAndSet(online)
        if (previous != online) {
            logger.info("Sending backend status {} for {}", if (online) "ONLINE" else "OFFLINE", config.serverId)
        }
        sendEnvelope(ControlMessageType.BACKEND_STATUS, ControlPayloadCodec.encodeBackendStatusNotice(notice))
    }

    private fun reject(reason: RejectReason, details: String, correlationId: String? = null) {
        rejectCounters.computeIfAbsent(reason) { LongAdder() }.increment()
        logger.warn(
            "{}",
            StructuredLog.event(
                category = "control-plane",
                severity = "WARN",
                reason = reason.name,
                correlationId = correlationId,
                fields = mapOf("details" to details),
            )
        )
    }

    private fun nextNonce(): ByteArray {
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE)
        random.nextBytes(nonce)
        return nonce
    }

    private fun envelopeCorrelationId(envelope: ControlEnvelope): String {
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(envelope.nonce)
        return "${envelope.senderId}:$nonce"
    }

    private enum class RejectReason {
        PACKET_TOO_LARGE,
        MALFORMED_ENVELOPE,
        INVALID_TIMESTAMP,
        PAYLOAD_TOO_LARGE,
        UNEXPECTED_SENDER,
        REPLAYED,
        INGRESS_BACKPRESSURE,
        UNSUPPORTED_TYPE,
        MALFORMED_PAYLOAD,
        OUTBOUND_PAYLOAD_TOO_LARGE,
    }

    private companion object {
        private const val STATUS_HEARTBEAT_SECONDS = 2L
        private const val OFFLINE_BURST_COUNT = 3
        private const val OFFLINE_BURST_DELAY_MILLIS = 30L
    }
}
