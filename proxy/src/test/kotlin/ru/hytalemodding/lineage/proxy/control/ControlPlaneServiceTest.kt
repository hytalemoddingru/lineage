/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.control

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.event.EventHandler
import ru.hytalemodding.lineage.api.event.player.PlayerAuthenticatedEvent
import ru.hytalemodding.lineage.api.event.security.TokenValidationEvent
import ru.hytalemodding.lineage.api.messaging.Channel
import ru.hytalemodding.lineage.api.messaging.Message
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.player.PlayerState
import ru.hytalemodding.lineage.proxy.config.MessagingConfig
import ru.hytalemodding.lineage.proxy.event.EventBusImpl
import ru.hytalemodding.lineage.proxy.observability.ProxyMetricsRegistry
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.shared.control.ControlChannels
import ru.hytalemodding.lineage.shared.control.ControlEnvelope
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlPayloadCodec
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import ru.hytalemodding.lineage.shared.control.TokenValidationNotice
import ru.hytalemodding.lineage.shared.control.TokenValidationResult
import ru.hytalemodding.lineage.shared.control.TransferRequest
import ru.hytalemodding.lineage.shared.control.TransferResult
import ru.hytalemodding.lineage.shared.control.TransferResultStatus
import ru.hytalemodding.lineage.shared.time.Clock
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ControlPlaneServiceTest {
    @Test
    fun tokenValidationFiresBeforeAuthenticatedEvent() {
        val events = mutableListOf<String>()
        val eventBus = EventBusImpl()
        val listener = object {
            @EventHandler
            fun onToken(event: TokenValidationEvent) {
                events.add("token")
            }

            @EventHandler
            fun onAuthenticated(event: PlayerAuthenticatedEvent) {
                events.add("auth")
            }
        }
        eventBus.register(listener)

        val playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01")
        val players = PlayerManagerImpl()
        players.getOrCreate(playerId, "test-user")
        val messaging = TestMessaging()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
        )
        val clock = FixedClock(1_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            clock = clock,
        )

        val notice = TokenValidationNotice(
            playerId = playerId,
            backendId = "backend-1",
            result = TokenValidationResult.ACCEPTED,
            reason = null,
        )
        val payload = ControlPayloadCodec.encodeTokenValidationNotice(notice)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TOKEN_VALIDATION,
            senderId = "backend-1",
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = payload,
        )
        val packet = ControlProtocol.encode(envelope)
        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, packet)

        assertEquals(listOf("token", "auth"), events)
        assertEquals(PlayerState.AUTHENTICATED, players.get(playerId)?.state)
        assertTrue(service.rejectCountersSnapshot().isEmpty())
    }

    @Test
    fun tracksDeterministicRejectReasons() {
        val eventBus = EventBusImpl()
        val players = PlayerManagerImpl()
        val messaging = TestMessaging()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
        )
        val clock = FixedClock(1_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            clock = clock,
        )

        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, byteArrayOf(1, 2, 3))

        val unsupported = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TRANSFER_REQUEST,
            senderId = "backend-1",
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE) { 42 },
            payload = byteArrayOf(1),
        )
        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, ControlProtocol.encode(unsupported))

        val counters = service.rejectCountersSnapshot()
        assertEquals(1L, counters["MALFORMED_ENVELOPE"])
        assertEquals(1L, counters["UNSUPPORTED_TYPE"])
    }

    @Test
    fun rejectsVersionMismatchEnvelopeDeterministically() {
        val eventBus = EventBusImpl()
        val players = PlayerManagerImpl()
        val messaging = TestMessaging()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
        )
        val clock = FixedClock(1_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            clock = clock,
        )

        val notice = TokenValidationNotice(
            playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01"),
            backendId = "backend-1",
            result = TokenValidationResult.ACCEPTED,
            reason = null,
        )
        val payload = ControlPayloadCodec.encodeTokenValidationNotice(notice)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TOKEN_VALIDATION,
            senderId = "backend-1",
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = payload,
        )
        val packet = ControlProtocol.encode(envelope).clone()
        packet[2] = (ControlProtocol.VERSION + 1).toByte()
        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, packet)

        val counters = service.rejectCountersSnapshot()
        assertEquals(1L, counters["MALFORMED_ENVELOPE"])
    }

    @Test
    fun rejectsUnexpectedSenderDeterministically() {
        val eventBus = EventBusImpl()
        val players = PlayerManagerImpl()
        val messaging = TestMessaging()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
        )
        val clock = FixedClock(1_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            allowedBackendSenderIds = setOf("backend-allowed"),
            clock = clock,
        )

        val notice = TokenValidationNotice(
            playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01"),
            backendId = "backend-1",
            result = TokenValidationResult.ACCEPTED,
            reason = null,
        )
        val payload = ControlPayloadCodec.encodeTokenValidationNotice(notice)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TOKEN_VALIDATION,
            senderId = "backend-other",
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = payload,
        )
        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, ControlProtocol.encode(envelope))

        val counters = service.rejectCountersSnapshot()
        assertEquals(1L, counters["UNEXPECTED_SENDER"])
    }

    @Test
    fun rejectedTokenValidationDoesNotMutatePlayerState() {
        val events = mutableListOf<String>()
        val eventBus = EventBusImpl()
        val listener = object {
            @EventHandler
            fun onToken(event: TokenValidationEvent) {
                events.add("token")
            }

            @EventHandler
            fun onAuthenticated(event: PlayerAuthenticatedEvent) {
                events.add("auth")
            }
        }
        eventBus.register(listener)

        val playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01")
        val players = PlayerManagerImpl()
        val player = players.getOrCreate(playerId, "test-user")
        player.state = PlayerState.HANDSHAKING
        val messaging = TestMessaging()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
        )
        val clock = MutableClock(100_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            allowedBackendSenderIds = setOf("backend-1"),
            clock = clock,
        )

        val notice = TokenValidationNotice(
            playerId = playerId,
            backendId = "backend-1",
            result = TokenValidationResult.ACCEPTED,
            reason = null,
        )
        val payload = ControlPayloadCodec.encodeTokenValidationNotice(notice)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TOKEN_VALIDATION,
            senderId = "backend-1",
            issuedAtMillis = 1_000L,
            ttlMillis = 1_000L,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = payload,
        )
        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, ControlProtocol.encode(envelope))

        assertEquals(PlayerState.HANDSHAKING, players.get(playerId)?.state)
        assertTrue(events.isEmpty())
        val counters = service.rejectCountersSnapshot()
        assertEquals(1L, counters["INVALID_TIMESTAMP"])
        assertFalse(counters.containsKey("MALFORMED_PAYLOAD"))
    }

    @Test
    fun sameInvalidTimestampCauseProducesSameOutcome() {
        val events = mutableListOf<String>()
        val eventBus = EventBusImpl()
        val listener = object {
            @EventHandler
            fun onToken(event: TokenValidationEvent) {
                events.add("token")
            }

            @EventHandler
            fun onAuthenticated(event: PlayerAuthenticatedEvent) {
                events.add("auth")
            }
        }
        eventBus.register(listener)

        val playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01")
        val players = PlayerManagerImpl()
        val player = players.getOrCreate(playerId, "test-user")
        player.state = PlayerState.HANDSHAKING
        val messaging = TestMessaging()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
        )
        val clock = MutableClock(100_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            allowedBackendSenderIds = setOf("backend-1"),
            clock = clock,
        )

        val notice = TokenValidationNotice(
            playerId = playerId,
            backendId = "backend-1",
            result = TokenValidationResult.ACCEPTED,
            reason = null,
        )
        val payload = ControlPayloadCodec.encodeTokenValidationNotice(notice)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TOKEN_VALIDATION,
            senderId = "backend-1",
            issuedAtMillis = 1_000L,
            ttlMillis = 1_000L,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = payload,
        )
        val packet = ControlProtocol.encode(envelope)

        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, packet)
        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, packet)

        assertEquals(PlayerState.HANDSHAKING, players.get(playerId)?.state)
        assertTrue(events.isEmpty())
        val counters = service.rejectCountersSnapshot()
        assertEquals(2L, counters["INVALID_TIMESTAMP"])
        assertFalse(counters.containsKey("MALFORMED_PAYLOAD"))
    }

    @Test
    fun rejectsWhenInboundControlPlaneLimitIsReached() {
        val playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val tokenEvents = AtomicInteger(0)
        val eventBus = EventBusImpl()
        val listener = object {
            @EventHandler
            fun onToken(event: TokenValidationEvent) {
                tokenEvents.incrementAndGet()
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
            }
        }
        eventBus.register(listener)

        val players = PlayerManagerImpl()
        players.getOrCreate(playerId, "test-user")
        val messaging = TestMessaging()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
            controlMaxInflight = 1,
        )
        val clock = FixedClock(1_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            allowedBackendSenderIds = setOf("backend-1"),
            clock = clock,
        )
        val firstPacket = tokenValidationPacket(
            playerId = playerId,
            backendId = "backend-1",
            senderId = "backend-1",
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonceSeed = 7,
        )
        val secondPacket = tokenValidationPacket(
            playerId = playerId,
            backendId = "backend-1",
            senderId = "backend-1",
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonceSeed = 9,
        )

        val first = Thread {
            messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, firstPacket)
        }
        first.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, secondPacket)
        release.countDown()
        first.join(2_000)

        val counters = service.rejectCountersSnapshot()
        assertEquals(1L, counters["INGRESS_BACKPRESSURE"])
        assertEquals(1, tokenEvents.get())
    }

    @Test
    fun recordsControlRejectAndMessagingLatencyMetrics() {
        val eventBus = EventBusImpl()
        val players = PlayerManagerImpl()
        val messaging = TestMessaging()
        val metrics = ProxyMetricsRegistry()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
        )
        val clock = MutableClock(1_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            allowedBackendSenderIds = setOf("backend-1"),
            metrics = metrics,
            clock = clock,
        )

        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, byteArrayOf(1, 2, 3))

        val request = TransferRequest(
            correlationId = UUID.fromString("6cf11956-f06f-4310-a7db-a2090f12c700"),
            playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01"),
            targetBackendId = "backend-1",
            referralData = byteArrayOf(1, 2, 3),
        )
        assertTrue(service.sendTransferRequest(request))
        clock.now = 1_012L
        val resultPayload = ControlPayloadCodec.encodeTransferResult(
            TransferResult(
                correlationId = request.correlationId,
                status = TransferResultStatus.OK,
                reason = null,
            )
        )
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TRANSFER_RESULT,
            senderId = "backend-1",
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE) { 3 },
            payload = resultPayload,
        )
        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, ControlProtocol.encode(envelope))

        val metricsOutput = metrics.renderPrometheus()
        assertTrue(metricsOutput.contains("lineage_proxy_control_reject_total{reason=\"MALFORMED_ENVELOPE\"} 1"))
        assertTrue(metricsOutput.contains("lineage_proxy_messaging_latency_ms_count 1.0"))
        assertTrue(metricsOutput.contains("lineage_proxy_messaging_latency_ms_sum 12.0"))
        assertTrue(metricsOutput.contains("lineage_proxy_messaging_latency_ms_max 12.0"))
    }

    @Test
    fun handlesTransferControlPlaneSmokeLoad() {
        val eventBus = EventBusImpl()
        val players = PlayerManagerImpl()
        val messaging = TestMessaging()
        val metrics = ProxyMetricsRegistry()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
            controlMaxInflight = 256,
        )
        val clock = MutableClock(1_000L)
        val service = ControlPlaneService(
            messaging = messaging,
            config = config,
            eventBus = eventBus,
            players = players,
            allowedBackendSenderIds = setOf("backend-1"),
            metrics = metrics,
            clock = clock,
        )
        val playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01")

        repeat(200) { index ->
            val request = TransferRequest(
                correlationId = UUID.randomUUID(),
                playerId = playerId,
                targetBackendId = "backend-1",
                referralData = byteArrayOf(1, 2, 3),
            )
            assertTrue(service.sendTransferRequest(request))
            clock.now += 1
            val resultPayload = ControlPayloadCodec.encodeTransferResult(
                TransferResult(
                    correlationId = request.correlationId,
                    status = TransferResultStatus.OK,
                    reason = null,
                )
            )
            val envelope = ControlEnvelope(
                version = ControlProtocol.VERSION,
                type = ControlMessageType.TRANSFER_RESULT,
                senderId = "backend-1",
                issuedAtMillis = clock.nowMillis(),
                ttlMillis = config.controlTtlMillis,
                nonce = ByteArray(ControlProtocol.NONCE_SIZE) { index.toByte() },
                payload = resultPayload,
            )
            messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, ControlProtocol.encode(envelope))
        }

        assertTrue(service.rejectCountersSnapshot().isEmpty())
        val metricsOutput = metrics.renderPrometheus()
        assertTrue(metricsOutput.contains("lineage_proxy_messaging_latency_ms_count 200.0"))
    }

    private fun tokenValidationPacket(
        playerId: UUID,
        backendId: String,
        senderId: String,
        issuedAtMillis: Long,
        ttlMillis: Long,
        nonceSeed: Int,
    ): ByteArray {
        val notice = TokenValidationNotice(
            playerId = playerId,
            backendId = backendId,
            result = TokenValidationResult.ACCEPTED,
            reason = null,
        )
        val payload = ControlPayloadCodec.encodeTokenValidationNotice(notice)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TOKEN_VALIDATION,
            senderId = senderId,
            issuedAtMillis = issuedAtMillis,
            ttlMillis = ttlMillis,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE) { nonceSeed.toByte() },
            payload = payload,
        )
        return ControlProtocol.encode(envelope)
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    private class MutableClock(var now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    private class TestMessaging : Messaging {
        private val handlers = mutableMapOf<String, MessageHandler>()

        override fun registerChannel(id: String, handler: MessageHandler): Channel {
            handlers[id] = handler
            return TestChannel(id)
        }

        override fun unregisterChannel(id: String) {
            handlers.remove(id)
        }

        override fun channel(id: String): Channel? {
            return handlers[id]?.let { TestChannel(id) }
        }

        fun deliver(id: String, payload: ByteArray) {
            handlers[id]?.onMessage(Message(id, payload))
        }
    }

    private class TestChannel(
        override val id: String,
    ) : Channel {
        override fun send(payload: ByteArray) {
        }
    }
}
