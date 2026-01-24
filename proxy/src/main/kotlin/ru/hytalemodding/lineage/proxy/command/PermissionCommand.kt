/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandFlag
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.permission.PermissionSubject
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl
import ru.hytalemodding.lineage.proxy.permission.PermissionStore

/**
 * Manages proxy permission assignments.
 */
class PermissionCommand(
    private val permissionChecker: PermissionCheckerImpl,
    private val players: PlayerManager,
    private val store: PermissionStore,
) : Command {
    override val name: String = "perm"
    override val aliases: List<String> = listOf("permission")
    override val description: String = "Manage proxy permissions"
    override val usage: String = "perm grant <player> <permission> | perm revoke <player> <permission> | perm clear <player> | perm list [player]"
    override val permission: String? = "lineage.command.perm"
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val args = context.args
        if (args.isEmpty()) {
            sendUsage(context.sender)
            return
        }
        when (args[0].lowercase()) {
            "grant" -> grant(context.sender, args.getOrNull(1), args.getOrNull(2))
            "revoke" -> revoke(context.sender, args.getOrNull(1), args.getOrNull(2))
            "clear" -> clear(context.sender, args.getOrNull(1))
            "list" -> list(context.sender, args.getOrNull(1))
            else -> sendUsage(context.sender)
        }
    }

    override fun suggest(context: CommandContext): List<String> {
        val args = context.args
        if (args.isEmpty()) {
            return listOf("grant", "revoke", "clear", "list")
        }
        if (args.size == 1) {
            val prefix = args[0].lowercase()
            return listOf("grant", "revoke", "clear", "list").filter { it.startsWith(prefix) }
        }
        if (args.size == 2) {
            val prefix = args[1].lowercase()
            return players.all()
                .map { it.username }
                .filter { it.lowercase().startsWith(prefix) }
        }
        return emptyList()
    }

    private fun grant(sender: CommandSender, targetName: String?, permission: String?) {
        if (targetName.isNullOrBlank() || permission.isNullOrBlank()) {
            sender.sendMessage("Usage: perm grant <player> <permission>")
            return
        }
        val subject = resolveSubject(targetName)
        permissionChecker.grant(subject, permission)
        store.save(permissionChecker.snapshot())
        sender.sendMessage("Granted $permission to ${subject.name}.")
    }

    private fun revoke(sender: CommandSender, targetName: String?, permission: String?) {
        if (targetName.isNullOrBlank() || permission.isNullOrBlank()) {
            sender.sendMessage("Usage: perm revoke <player> <permission>")
            return
        }
        val subject = resolveSubject(targetName)
        permissionChecker.revoke(subject, permission)
        store.save(permissionChecker.snapshot())
        sender.sendMessage("Revoked $permission from ${subject.name}.")
    }

    private fun clear(sender: CommandSender, targetName: String?) {
        if (targetName.isNullOrBlank()) {
            sender.sendMessage("Usage: perm clear <player>")
            return
        }
        val subject = resolveSubject(targetName)
        permissionChecker.clear(subject)
        store.save(permissionChecker.snapshot())
        sender.sendMessage("Cleared permissions for ${subject.name}.")
    }

    private fun list(sender: CommandSender, targetName: String?) {
        if (targetName.isNullOrBlank()) {
            sender.sendMessage("Usage: perm list <player>")
            return
        }
        val subject = resolveSubject(targetName)
        val permissions = permissionChecker.list(subject).sorted()
        if (permissions.isEmpty()) {
            sender.sendMessage("No permissions for ${subject.name}.")
            return
        }
        sender.sendMessage("Permissions for ${subject.name}:")
        permissions.forEach { sender.sendMessage("- $it") }
    }

    private fun resolveSubject(name: String): PermissionSubject {
        val player = players.getByName(name)
        val actualName = player?.username ?: name
        return object : PermissionSubject {
            override val name: String = actualName
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("Usage: $usage")
    }
}
