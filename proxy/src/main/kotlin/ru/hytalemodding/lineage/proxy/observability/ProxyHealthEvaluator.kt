/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.observability

class ProxyHealthEvaluator(
    private val listenerActive: () -> Boolean,
    private val messagingEnabled: Boolean,
    private val messagingActive: () -> Boolean,
) {
    fun snapshot(): HealthSnapshot {
        val listenerState = if (listenerActive()) "UP" else "DOWN"
        val messagingState = when {
            !messagingEnabled -> "DISABLED"
            messagingActive() -> "UP"
            else -> "DOWN"
        }
        val status = when {
            listenerState == "DOWN" -> HealthStatus.FAILED
            messagingEnabled && messagingState == "DOWN" -> HealthStatus.DEGRADED
            else -> HealthStatus.READY
        }
        return HealthSnapshot(
            status = status,
            listener = listenerState,
            messaging = messagingState,
        )
    }
}

