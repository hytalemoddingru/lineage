/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.service

/**
 * Stores and resolves shared services between mods.
 */
interface ServiceRegistry {
    fun <T : Any> register(key: ServiceKey<T>, service: T)
    fun <T : Any> get(key: ServiceKey<T>): T?
    fun <T : Any> unregister(key: ServiceKey<T>)
    fun keys(): Set<ServiceKey<*>>
}
