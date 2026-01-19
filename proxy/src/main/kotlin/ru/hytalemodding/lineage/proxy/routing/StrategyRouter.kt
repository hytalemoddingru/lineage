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
import ru.hytalemodding.lineage.api.service.ServiceRegistry
import ru.hytalemodding.lineage.proxy.config.BackendConfig
import ru.hytalemodding.lineage.proxy.config.ProxyConfig
import ru.hytalemodding.lineage.proxy.util.Logging

/**
 * Router that delegates backend selection to a pluggable strategy.
 */
class StrategyRouter(
    config: ProxyConfig,
    private val serviceRegistry: ServiceRegistry,
    private val fallback: RoutingStrategy,
) : Router {
    private val logger = Logging.logger(StrategyRouter::class.java)
    private val backendsById = config.backends.associateBy { it.id }
    private val defaultBackendId = config.routing.defaultBackendId

    override fun selectInitialBackend(context: RoutingContext): BackendConfig {
        val strategy = resolveStrategy()
        val backendId = strategy.selectInitialBackend(context)
        return resolveBackend(backendId)
    }

    override fun selectBackend(context: RoutingContext): BackendConfig {
        val strategy = resolveStrategy()
        val backendId = strategy.selectBackend(context)
        return resolveBackend(backendId)
    }

    override fun findBackend(id: String) = backendsById[id]

    private fun resolveStrategy(): RoutingStrategy {
        return serviceRegistry.get(RoutingStrategy.SERVICE_KEY) ?: fallback
    }

    private fun resolveBackend(backendId: String): BackendConfig {
        val resolved = backendsById[backendId]
        if (resolved != null) {
            return resolved
        }
        logger.warn("Routing strategy returned unknown backend {}, falling back to {}", backendId, defaultBackendId)
        return backendsById[defaultBackendId]
            ?: throw IllegalStateException("Default backend not found: $defaultBackendId")
    }
}
