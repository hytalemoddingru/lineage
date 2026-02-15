/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.observability

enum class HealthStatus {
    READY,
    DEGRADED,
    FAILED,
}

data class HealthSnapshot(
    val status: HealthStatus,
    val listener: String,
    val messaging: String,
)

