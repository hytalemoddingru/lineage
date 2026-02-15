/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.observability

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyMetricsRegistryTest {
    @Test
    fun rendersCoreMetrics() {
        val metrics = ProxyMetricsRegistry()
        metrics.bindSessionsGauge { 3 }
        metrics.bindPlayersGauge { 2 }
        metrics.bindMessagingEnabledGauge { true }
        metrics.bindMessagingRunningGauge { true }
        metrics.incrementHandshakeError("HANDSHAKE_RATE_LIMIT")
        metrics.incrementControlReject("INVALID_TIMESTAMP")
        metrics.incrementRoutingDecision("STRATEGY")
        metrics.recordMessagingLatencyMillis(12)
        metrics.recordMessagingLatencyMillis(25)

        val output = metrics.renderPrometheus()

        assertTrue(output.contains("lineage_proxy_sessions_active 3.0"))
        assertTrue(output.contains("lineage_proxy_players_active 2.0"))
        assertTrue(output.contains("lineage_proxy_messaging_enabled 1.0"))
        assertTrue(output.contains("lineage_proxy_messaging_running 1.0"))
        assertTrue(output.contains("lineage_proxy_handshake_errors_total{reason=\"HANDSHAKE_RATE_LIMIT\"} 1"))
        assertTrue(output.contains("lineage_proxy_control_reject_total{reason=\"INVALID_TIMESTAMP\"} 1"))
        assertTrue(output.contains("lineage_proxy_routing_decisions_total{reason=\"STRATEGY\"} 1"))
        assertTrue(output.contains("lineage_proxy_messaging_latency_ms_count 2.0"))
        assertTrue(output.contains("lineage_proxy_messaging_latency_ms_sum 37.0"))
        assertTrue(output.contains("lineage_proxy_messaging_latency_ms_max 25.0"))
    }
}

