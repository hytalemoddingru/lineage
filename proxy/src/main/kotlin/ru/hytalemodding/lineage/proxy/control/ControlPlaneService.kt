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
import ru.hytalemodding.lineage.proxy.observability.ProxyMetricsRegistry
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityTracker
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
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

class ControlPlaneService(
    messaging: Messaging,
    private val config: MessagingConfig,
    private val eventBus: EventBus,
    private val players: PlayerManagerImpl,
    private val backendAvailabilityTracker: BackendAvailabilityTracker? = null,
    private val allowedBackendSenderIds: Set<String> = emptySet(),
    private val metrics: ProxyMetricsRegistry? = null,
    private val clock: Clock = SystemClock,
) : TransferRequestSender {
    private val logger = Logging.logger(ControlPlaneService::class.java)
    private val rejectCounters = ConcurrentHashMap<RejectReason, LongAdder>()
    private val replayProtector = ControlReplayProtector(
        config.controlReplayWindowMillis,
        config.controlReplayMaxEntries,
        clock,
    )
    private val inboundInFlight = AtomicInteger(0)
    private val pendingTransferRequests = ConcurrentHashMap<java.util.UUID, Long>()
    private val backendReportedStatus = ConcurrentHashMap<String, Boolean>()
    private val random = SecureRandom()
    private val channel = messaging.registerChannel(
        ControlChannels.CONTROL_CHANNEL_ID,
        MessageHandler { message -> handleMessage(message.payload) }
    )

    override fun sendTransferRequest(request: TransferRequest): Boolean {
        val payload = ControlPayloadCodec.encodeTransferRequest(request)
        if (payload.size > config.controlMaxPayload) {
            reject(RejectReason.OUTBOUND_PAYLOAD_TOO_LARGE, "transfer request payload too large")
            return false
        }
        val envelope = buildEnvelope(ControlMessageType.TRANSFER_REQUEST, payload)
        pendingTransferRequests[request.correlationId] = clock.nowMillis()
        channel.send(ControlProtocol.encode(envelope))
        return true
    }

    internal fun rejectCountersSnapshot(): Map<String, Long> {
        return rejectCounters.entries.associate { it.key.name to it.value.sum() }
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
            val envelope = ControlProtocol.decode(payload) ?: run {
                reject(RejectReason.MALFORMED_ENVELOPE, "failed to decode control envelope")
                return
            }
            val correlationId = envelopeCorrelationId(envelope)
            if (allowedBackendSenderIds.isNotEmpty() && envelope.senderId !in allowedBackendSenderIds) {
                reject(
                    RejectReason.UNEXPECTED_SENDER,
                    "unexpected sender ${envelope.senderId}; expected one of $allowedBackendSenderIds",
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
                ControlMessageType.TRANSFER_RESULT -> handleTransferResult(envelope)
                ControlMessageType.TOKEN_VALIDATION -> handleTokenValidation(envelope)
                ControlMessageType.BACKEND_STATUS -> handleBackendStatus(envelope)
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

    private fun handleTransferResult(envelope: ControlEnvelope) {
        val result = ControlPayloadCodec.decodeTransferResult(envelope.payload) ?: run {
            reject(
                RejectReason.MALFORMED_PAYLOAD,
                "failed to decode TRANSFER_RESULT payload",
                envelopeCorrelationId(envelope),
            )
            return
        }
        logger.debug(
            "{}",
            StructuredLog.event(
                category = "control-plane",
                severity = "DEBUG",
                reason = "TRANSFER_RESULT",
                correlationId = result.correlationId.toString(),
                fields = mapOf(
                    "status" to result.status.name,
                    "failureReason" to (result.reason?.name ?: "NONE"),
                ),
            )
        )
        val startedAt = pendingTransferRequests.remove(result.correlationId)
        if (startedAt != null) {
            metrics?.recordMessagingLatencyMillis((clock.nowMillis() - startedAt).coerceAtLeast(0))
        }
    }

    private fun handleTokenValidation(envelope: ControlEnvelope) {
        val notice = ControlPayloadCodec.decodeTokenValidationNotice(envelope.payload) ?: run {
            reject(
                RejectReason.MALFORMED_PAYLOAD,
                "failed to decode TOKEN_VALIDATION payload",
                envelopeCorrelationId(envelope),
            )
            return
        }
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

    private fun handleBackendStatus(envelope: ControlEnvelope) {
        val notice = ControlPayloadCodec.decodeBackendStatusNotice(envelope.payload) ?: run {
            reject(
                RejectReason.MALFORMED_PAYLOAD,
                "failed to decode BACKEND_STATUS payload",
                envelopeCorrelationId(envelope),
            )
            return
        }
        val tracker = backendAvailabilityTracker ?: return
        val previous = backendReportedStatus.put(notice.backendId, notice.online)
        if (notice.online) {
            tracker.markReportedOnline(notice.backendId)
            if (previous != true) {
                logger.info("Backend status changed [{} -> ONLINE]", notice.backendId)
            }
        } else {
            tracker.markReportedOffline(notice.backendId)
            if (previous != false) {
                logger.warn("Backend status changed [{} -> OFFLINE]", notice.backendId)
            }
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

    private fun reject(reason: RejectReason, details: String, correlationId: String? = null) {
        rejectCounters.computeIfAbsent(reason) { LongAdder() }.increment()
        metrics?.incrementControlReject(reason.name)
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

    private fun envelopeCorrelationId(envelope: ControlEnvelope): String {
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(envelope.nonce)
        return "${envelope.senderId}:$nonce"
    }

    private enum class RejectReason {
        PACKET_TOO_LARGE,
        MALFORMED_ENVELOPE,
        INVALID_TIMESTAMP,
        UNEXPECTED_SENDER,
        PAYLOAD_TOO_LARGE,
        REPLAYED,
        INGRESS_BACKPRESSURE,
        UNSUPPORTED_TYPE,
        MALFORMED_PAYLOAD,
        OUTBOUND_PAYLOAD_TOO_LARGE,
    }
}
