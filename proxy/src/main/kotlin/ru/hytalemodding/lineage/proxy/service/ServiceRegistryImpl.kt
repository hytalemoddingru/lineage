/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.service

import ru.hytalemodding.lineage.api.service.ServiceKey
import ru.hytalemodding.lineage.api.service.ServiceRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe service registry implementation.
 */
class ServiceRegistryImpl : ServiceRegistry {
    private val services = ConcurrentHashMap<ServiceKey<*>, Any>()

    override fun <T : Any> register(key: ServiceKey<T>, service: T) {
        services[key] = service
    }

    override fun <T : Any> get(key: ServiceKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return services[key] as? T
    }

    override fun <T : Any> unregister(key: ServiceKey<T>) {
        services.remove(key)
    }

    override fun keys(): Set<ServiceKey<*>> = services.keys
}
