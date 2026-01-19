/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.routing

import ru.hytalemodding.lineage.api.service.ServiceKey

/**
 * Strategy for selecting backends during connect and transfer.
 */
interface RoutingStrategy {
    fun selectInitialBackend(context: RoutingContext): String
    fun selectBackend(context: RoutingContext): String

    companion object {
        val SERVICE_KEY = ServiceKey(RoutingStrategy::class.java)
    }
}
