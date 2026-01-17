/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.command

/**
 * Execution context for a command invocation.
 */
interface CommandContext {
    val sender: CommandSender
    val input: String
    val args: List<String>

    fun arg(index: Int): String?
    fun hasPermission(permission: String): Boolean
}
