/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.control

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.backend.config.BackendConfig
import ru.hytalemodding.lineage.shared.control.ControlEnvelope
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackendControlPlaneServiceTest {
    @Test
    fun rejectsVersionMismatchEnvelopeDeterministically() {
        val service = BackendControlPlaneService(testConfig())
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TRANSFER_REQUEST,
            senderId = "proxy",
            issuedAtMillis = 1_000L,
            ttlMillis = 10_000L,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = byteArrayOf(1),
        )
        val packet = ControlProtocol.encode(envelope).clone()
        packet[2] = (ControlProtocol.VERSION + 1).toByte()

        invokeHandleMessage(service, packet)

        val counters = service.rejectCountersSnapshot()
        assertEquals(1L, counters["MALFORMED_ENVELOPE"])
    }

    @Test
    fun rejectsUnexpectedSenderDeterministically() {
        val service = BackendControlPlaneService(testConfig())
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TRANSFER_REQUEST,
            senderId = "backend-1",
            issuedAtMillis = 1_000L,
            ttlMillis = 10_000L,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = byteArrayOf(1),
        )

        invokeHandleMessage(service, ControlProtocol.encode(envelope))

        val counters = service.rejectCountersSnapshot()
        assertEquals(1L, counters["UNEXPECTED_SENDER"])
    }

    @Test
    fun sameUnexpectedSenderCauseProducesSameOutcome() {
        val service = BackendControlPlaneService(testConfig())
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TRANSFER_REQUEST,
            senderId = "backend-1",
            issuedAtMillis = 1_000L,
            ttlMillis = 10_000L,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = byteArrayOf(1),
        )
        val packet = ControlProtocol.encode(envelope)

        invokeHandleMessage(service, packet)
        invokeHandleMessage(service, packet)

        val counters = service.rejectCountersSnapshot()
        assertEquals(2L, counters["UNEXPECTED_SENDER"])
        assertEquals(1, counters.size)
    }

    @Test
    fun rejectsWhenInboundControlPlaneLimitIsReached() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val service = BackendControlPlaneService(
            config = testConfig().copy(controlMaxInflight = 1),
            inboundProcessingHook = {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
            },
        )
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TRANSFER_REQUEST,
            senderId = "proxy",
            issuedAtMillis = 1_000L,
            ttlMillis = 10_000L,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = byteArrayOf(1),
        )
        val packet = ControlProtocol.encode(envelope)

        val first = Thread {
            invokeHandleMessage(service, packet)
        }
        first.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        invokeHandleMessage(service, packet)
        release.countDown()
        first.join(2_000)

        val counters = service.rejectCountersSnapshot()
        assertEquals(1L, counters["INGRESS_BACKPRESSURE"])
    }

    private fun invokeHandleMessage(service: BackendControlPlaneService, payload: ByteArray) {
        val method = BackendControlPlaneService::class.java.getDeclaredMethod("handleMessage", ByteArray::class.java)
        method.isAccessible = true
        method.invoke(service, payload)
    }

    private fun testConfig(): BackendConfig {
        return BackendConfig(
            schemaVersion = 1,
            serverId = "hub",
            proxySecret = "secret-123",
            previousProxySecret = null,
            proxyConnectHost = "127.0.0.1",
            proxyConnectPort = 25565,
            messagingHost = "127.0.0.1",
            messagingPort = 25570,
            messagingEnabled = true,
            controlSenderId = "hub",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 100_000,
            controlMaxSkewMillis = 120_000L,
            controlTtlMillis = 10_000L,
            controlMaxInflight = 256,
            requireAuthenticatedMode = true,
            enforceProxy = true,
            referralSourceHost = "127.0.0.1",
            referralSourcePort = 25565,
            replayWindowMillis = 10_000L,
            replayMaxEntries = 100_000,
        )
    }
}
