/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.routing

import ru.hytalemodding.lineage.api.routing.RoutingContext
import ru.hytalemodding.lineage.api.routing.RoutingStrategy
import ru.hytalemodding.lineage.proxy.config.ProxyConfig

/**
 * Default routing strategy based on configuration.
 */
class StaticRoutingStrategy(
    private val config: ProxyConfig,
) : RoutingStrategy {
    override fun selectInitialBackend(context: RoutingContext): String {
        return config.routing.defaultBackendId
    }

    override fun selectBackend(context: RoutingContext): String {
        return context.requestedBackendId ?: config.routing.defaultBackendId
    }
}
