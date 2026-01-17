/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.command

/**
 * Registers commands and resolves them by name.
 */
interface CommandRegistry {
    fun register(command: Command)
    fun unregister(name: String)
    fun get(name: String): Command?
}
