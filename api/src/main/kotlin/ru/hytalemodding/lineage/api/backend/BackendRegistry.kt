/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.backend

/**
 * Provides access to configured backend servers.
 */
interface BackendRegistry {
    fun get(id: String): BackendInfo?
    fun all(): Collection<BackendInfo>
}
