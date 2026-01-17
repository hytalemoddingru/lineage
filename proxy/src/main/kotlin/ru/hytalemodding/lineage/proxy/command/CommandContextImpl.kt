/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.permission.PermissionChecker

/**
 * Default command execution context.
 */
class CommandContextImpl(
    override val sender: CommandSender,
    override val input: String,
    override val args: List<String>,
    private val permissionChecker: PermissionChecker,
) : CommandContext {
    override fun arg(index: Int): String? = args.getOrNull(index)

    override fun hasPermission(permission: String): Boolean {
        return permissionChecker.hasPermission(sender, permission)
    }
}
