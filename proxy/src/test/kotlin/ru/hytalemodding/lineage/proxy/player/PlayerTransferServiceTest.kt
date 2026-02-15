/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.proxy.control.TransferRequestSender
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityTracker
import ru.hytalemodding.lineage.proxy.security.TransferTokenIssuer
import ru.hytalemodding.lineage.shared.control.TransferRequest
import java.util.UUID

class PlayerTransferServiceTest {
    @Test
    fun rejectsTransferWhenBackendStatusUnknown() {
        var sent = false
        val tracker = BackendAvailabilityTracker(knownBackendIds = setOf("hub", "survival"))
        val service = PlayerTransferService(
            eventBus = ru.hytalemodding.lineage.proxy.event.EventBusImpl(),
            requestSender = object : TransferRequestSender {
                override fun sendTransferRequest(request: TransferRequest): Boolean {
                    sent = true
                    return true
                }
            },
            tokenIssuer = TransferTokenIssuer("secret-123".toByteArray()),
            knownBackendIds = listOf("hub", "survival"),
            backendAvailabilityTracker = tracker,
        )
        val player = ProxyPlayerImpl(UUID.randomUUID(), "tester", { service }) { { _, _ -> true } }
        player.backendId = "hub"

        val result = service.requestTransferDetailed(player, "survival")

        assertFalse(result.accepted)
        assertEquals(TransferRequestFailureReason.BACKEND_STATUS_UNKNOWN, result.reason)
        assertFalse(sent)
    }

    @Test
    fun sendsTransferWhenBackendOnline() {
        var sentRequest: TransferRequest? = null
        val tracker = BackendAvailabilityTracker(knownBackendIds = setOf("hub", "survival"))
        tracker.markReportedOnline("survival")
        val service = PlayerTransferService(
            eventBus = ru.hytalemodding.lineage.proxy.event.EventBusImpl(),
            requestSender = object : TransferRequestSender {
                override fun sendTransferRequest(request: TransferRequest): Boolean {
                    sentRequest = request
                    return true
                }
            },
            tokenIssuer = TransferTokenIssuer("secret-123".toByteArray()),
            knownBackendIds = listOf("hub", "survival"),
            backendAvailabilityTracker = tracker,
        )
        val player = ProxyPlayerImpl(UUID.randomUUID(), "tester", { service }) { { _, _ -> true } }
        player.backendId = "hub"

        val result = service.requestTransferDetailed(player, "Survival")

        assertTrue(result.accepted)
        assertEquals("survival", result.targetBackendId)
        assertEquals("survival", sentRequest?.targetBackendId)
    }

    @Test
    fun rejectsTransferWhenBackendOffline() {
        var sent = false
        val tracker = BackendAvailabilityTracker(knownBackendIds = setOf("hub", "survival"))
        tracker.markReportedOffline("survival")
        val service = PlayerTransferService(
            eventBus = ru.hytalemodding.lineage.proxy.event.EventBusImpl(),
            requestSender = object : TransferRequestSender {
                override fun sendTransferRequest(request: TransferRequest): Boolean {
                    sent = true
                    return true
                }
            },
            tokenIssuer = TransferTokenIssuer("secret-123".toByteArray()),
            knownBackendIds = listOf("hub", "survival"),
            backendAvailabilityTracker = tracker,
        )
        val player = ProxyPlayerImpl(UUID.randomUUID(), "tester", { service }) { { _, _ -> true } }
        player.backendId = "hub"

        val result = service.requestTransferDetailed(player, "survival")

        assertFalse(result.accepted)
        assertEquals(TransferRequestFailureReason.BACKEND_UNAVAILABLE, result.reason)
        assertFalse(sent)
    }
}
