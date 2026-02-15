/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProxyHealthEvaluatorTest {
    @Test
    fun reportsReadyWhenListenerIsUpAndMessagingDisabled() {
        val evaluator = ProxyHealthEvaluator(
            listenerActive = { true },
            messagingEnabled = false,
            messagingActive = { false },
        )

        val snapshot = evaluator.snapshot()

        assertEquals(HealthStatus.READY, snapshot.status)
        assertEquals("UP", snapshot.listener)
        assertEquals("DISABLED", snapshot.messaging)
    }

    @Test
    fun reportsDegradedWhenMessagingIsRequiredButDown() {
        val evaluator = ProxyHealthEvaluator(
            listenerActive = { true },
            messagingEnabled = true,
            messagingActive = { false },
        )

        val snapshot = evaluator.snapshot()

        assertEquals(HealthStatus.DEGRADED, snapshot.status)
        assertEquals("UP", snapshot.listener)
        assertEquals("DOWN", snapshot.messaging)
    }

    @Test
    fun reportsFailedWhenListenerIsDown() {
        val evaluator = ProxyHealthEvaluator(
            listenerActive = { false },
            messagingEnabled = true,
            messagingActive = { true },
        )

        val snapshot = evaluator.snapshot()

        assertEquals(HealthStatus.FAILED, snapshot.status)
        assertEquals("DOWN", snapshot.listener)
        assertEquals("UP", snapshot.messaging)
    }
}

