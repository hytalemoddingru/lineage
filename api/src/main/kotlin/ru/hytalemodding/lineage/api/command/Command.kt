/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.command

/**
 * Command definition registered in the proxy.
 */
interface Command {
    val name: String
    val aliases: List<String>
    val description: String
    val usage: String
    val permission: String?
    val flags: Set<CommandFlag>

    fun execute(context: CommandContext)
    fun suggest(context: CommandContext): List<String>
}
