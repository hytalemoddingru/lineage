/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.backend

import ru.hytalemodding.lineage.api.backend.BackendInfo
import ru.hytalemodding.lineage.api.backend.BackendRegistry
import ru.hytalemodding.lineage.proxy.config.ProxyConfig

/**
 * Exposes backend configuration to mods.
 */
class BackendRegistryImpl(config: ProxyConfig) : BackendRegistry {
    private val backends = config.backends.associateBy { it.id }

    override fun get(id: String): BackendInfo? {
        val backend = backends[id] ?: return null
        return BackendInfo(id = backend.id, host = backend.host, port = backend.port)
    }

    override fun all(): Collection<BackendInfo> = backends.values.map {
        BackendInfo(id = it.id, host = it.host, port = it.port)
    }
}
