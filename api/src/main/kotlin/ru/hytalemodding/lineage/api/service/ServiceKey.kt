/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.service

/**
 * Identifies a service instance in the registry.
 */
data class ServiceKey<T : Any>(
    val type: Class<T>,
    val name: String = "default",
)
