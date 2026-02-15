/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.observability

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

class ProxyMetricsRegistry {
    private val handshakeErrors = ConcurrentHashMap<String, LongAdder>()
    private val controlRejects = ConcurrentHashMap<String, LongAdder>()
    private val routingDecisions = ConcurrentHashMap<String, LongAdder>()
    private val messagingLatencyCount = LongAdder()
    private val messagingLatencySumMillis = LongAdder()
    private val messagingLatencyMaxMillis = AtomicLong(0L)

    @Volatile
    private var sessionsGauge: () -> Int = { 0 }

    @Volatile
    private var playersGauge: () -> Int = { 0 }

    @Volatile
    private var messagingEnabledGauge: () -> Boolean = { false }

    @Volatile
    private var messagingRunningGauge: () -> Boolean = { false }

    fun bindSessionsGauge(gauge: () -> Int) {
        sessionsGauge = gauge
    }

    fun bindPlayersGauge(gauge: () -> Int) {
        playersGauge = gauge
    }

    fun bindMessagingEnabledGauge(gauge: () -> Boolean) {
        messagingEnabledGauge = gauge
    }

    fun bindMessagingRunningGauge(gauge: () -> Boolean) {
        messagingRunningGauge = gauge
    }

    fun incrementHandshakeError(reason: String) {
        incrementCounter(handshakeErrors, reason)
    }

    fun incrementControlReject(reason: String) {
        incrementCounter(controlRejects, reason)
    }

    fun incrementRoutingDecision(reason: String) {
        incrementCounter(routingDecisions, reason)
    }

    fun recordMessagingLatencyMillis(latencyMillis: Long) {
        if (latencyMillis < 0) {
            return
        }
        messagingLatencyCount.increment()
        messagingLatencySumMillis.add(latencyMillis)
        messagingLatencyMaxMillis.accumulateAndGet(latencyMillis, ::maxOf)
    }

    fun renderPrometheus(): String {
        val builder = StringBuilder(1024)
        appendGauge(builder, "lineage_proxy_sessions_active", sessionsGauge().toDouble())
        appendGauge(builder, "lineage_proxy_players_active", playersGauge().toDouble())
        appendGauge(builder, "lineage_proxy_messaging_enabled", if (messagingEnabledGauge()) 1.0 else 0.0)
        appendGauge(builder, "lineage_proxy_messaging_running", if (messagingRunningGauge()) 1.0 else 0.0)
        appendCounterMap(builder, "lineage_proxy_handshake_errors_total", handshakeErrors)
        appendCounterMap(builder, "lineage_proxy_control_reject_total", controlRejects)
        appendCounterMap(builder, "lineage_proxy_routing_decisions_total", routingDecisions)
        appendGauge(builder, "lineage_proxy_messaging_latency_ms_count", messagingLatencyCount.sum().toDouble())
        appendGauge(builder, "lineage_proxy_messaging_latency_ms_sum", messagingLatencySumMillis.sum().toDouble())
        appendGauge(builder, "lineage_proxy_messaging_latency_ms_max", messagingLatencyMaxMillis.get().toDouble())
        return builder.toString()
    }

    private fun incrementCounter(target: ConcurrentHashMap<String, LongAdder>, reason: String) {
        target.computeIfAbsent(reason) { LongAdder() }.increment()
    }

    private fun appendCounterMap(
        builder: StringBuilder,
        metricName: String,
        values: ConcurrentHashMap<String, LongAdder>,
    ) {
        builder.append("# TYPE ").append(metricName).append(" counter\n")
        values.entries
            .sortedBy { it.key }
            .forEach { entry ->
                builder.append(metricName)
                    .append("{reason=\"")
                    .append(escapeLabel(entry.key))
                    .append("\"} ")
                    .append(entry.value.sum())
                    .append('\n')
            }
    }

    private fun appendGauge(builder: StringBuilder, metricName: String, value: Double) {
        builder.append("# TYPE ").append(metricName).append(" gauge\n")
            .append(metricName).append(' ').append(value).append('\n')
    }

    private fun escapeLabel(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}

