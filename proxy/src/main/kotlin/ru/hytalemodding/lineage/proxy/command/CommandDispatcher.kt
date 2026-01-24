/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.CommandFlag
import ru.hytalemodding.lineage.api.command.CommandRegistry
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.api.permission.PermissionChecker

/**
 * Parses and dispatches command input.
 */
class CommandDispatcher(
    private val registry: CommandRegistry,
    private val permissionChecker: PermissionChecker,
) {
    fun dispatch(sender: CommandSender, input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        val parts = trimmed.split(Regex("\\s+"))
        val name = parts.first()
        val command = registry.get(name) ?: return false
        val args = if (parts.size > 1) parts.drop(1) else emptyList()
        val context = CommandContextImpl(sender, trimmed, args, permissionChecker)
        if (CommandFlag.PLAYER_ONLY in command.flags && sender.type != SenderType.PLAYER) {
            sender.sendMessage("Command is only available to players.")
            return true
        }
        val permission = command.permission
        if (permission != null && !context.hasPermission(permission)) {
            sender.sendMessage("You do not have permission to run this command.")
            return true
        }
        command.execute(context)
        return true
    }
}
