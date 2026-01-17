/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.config

/**
 * Provides access to mod-scoped configuration files.
 */
interface ConfigManager {
    fun config(
        name: String,
        createIfMissing: Boolean = false,
        defaults: (() -> String)? = null,
    ): ModConfig

    fun reloadAll()
    fun saveAll()
}
