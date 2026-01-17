/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.routing

import ru.hytalemodding.lineage.proxy.config.ProxyConfig

/**
 * Static router that selects backends directly from configuration.
 */
class StaticRouter(
    private val config: ProxyConfig,
) : Router {
    private val backendsById = config.backends.associateBy { it.id }

    override fun selectInitialBackend() =
        backendsById[config.routing.defaultBackendId]
            ?: throw IllegalStateException(
                "Default backend not found: ${config.routing.defaultBackendId}",
            )

    override fun findBackend(id: String) = backendsById[id]
}
