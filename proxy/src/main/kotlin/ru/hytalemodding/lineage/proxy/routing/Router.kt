/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.routing

import ru.hytalemodding.lineage.proxy.config.BackendConfig

/**
 * Selects backend servers for incoming connections or transfers.
 */
interface Router {
    /**
     * Returns the initial backend for a new connection.
     */
    fun selectInitialBackend(): BackendConfig

    /**
     * Finds a backend by its identifier, or null if not found.
     */
    fun findBackend(id: String): BackendConfig?
}
