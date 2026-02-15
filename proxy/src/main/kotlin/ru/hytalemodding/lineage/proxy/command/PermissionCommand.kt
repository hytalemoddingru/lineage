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
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl
import ru.hytalemodding.lineage.proxy.permission.PermissionStore

/**
 * Manages proxy permission assignments.
 */
class PermissionCommand(
    private val permissionChecker: PermissionCheckerImpl,
    private val players: PlayerManager,
    private val store: PermissionStore,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "perm"
    override val aliases: List<String> = listOf("permission")
    override val description: String = "Manage proxy permissions"
    override val usage: String = "perm grant <player> <permission> | perm revoke <player> <permission> | perm clear <player> | perm list [player]"
    override val permission: String? = "lineage.command.perm"
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val language = (context.sender as? ProxyPlayerCommandSender)?.language
        val args = context.args
        if (args.isEmpty()) {
            sendUsage(context.sender, language)
            return
        }
        when (args[0].lowercase()) {
            "grant" -> grant(context.sender, language, args.getOrNull(1), args.getOrNull(2))
            "revoke" -> revoke(context.sender, language, args.getOrNull(1), args.getOrNull(2))
            "clear" -> clear(context.sender, language, args.getOrNull(1))
            "list" -> list(context.sender, language, args.getOrNull(1))
            else -> sendUsage(context.sender, language)
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

    private fun grant(sender: CommandSender, language: String?, targetName: String?, permission: String?) {
        if (targetName.isNullOrBlank() || permission.isNullOrBlank()) {
            sender.sendMessage(messages.text(language, "perm_usage_grant"))
            return
        }
        val subject = resolveSubject(targetName)
        permissionChecker.grant(subject, permission)
        store.save(permissionChecker.snapshot())
        sender.sendMessage(
            messages.text(
                language,
                "perm_granted",
                mapOf("permission" to permission, "subject" to subject.name),
            )
        )
    }

    private fun revoke(sender: CommandSender, language: String?, targetName: String?, permission: String?) {
        if (targetName.isNullOrBlank() || permission.isNullOrBlank()) {
            sender.sendMessage(messages.text(language, "perm_usage_revoke"))
            return
        }
        val subject = resolveSubject(targetName)
        permissionChecker.revoke(subject, permission)
        store.save(permissionChecker.snapshot())
        sender.sendMessage(
            messages.text(
                language,
                "perm_revoked",
                mapOf("permission" to permission, "subject" to subject.name),
            )
        )
    }

    private fun clear(sender: CommandSender, language: String?, targetName: String?) {
        if (targetName.isNullOrBlank()) {
            sender.sendMessage(messages.text(language, "perm_usage_clear"))
            return
        }
        val subject = resolveSubject(targetName)
        permissionChecker.clear(subject)
        store.save(permissionChecker.snapshot())
        sender.sendMessage(messages.text(language, "perm_cleared", mapOf("subject" to subject.name)))
    }

    private fun list(sender: CommandSender, language: String?, targetName: String?) {
        if (targetName.isNullOrBlank()) {
            sender.sendMessage(messages.text(language, "perm_usage_list"))
            return
        }
        val subject = resolveSubject(targetName)
        val permissions = permissionChecker.list(subject).sorted()
        if (permissions.isEmpty()) {
            sender.sendMessage(messages.text(language, "perm_none", mapOf("subject" to subject.name)))
            return
        }
        sender.sendMessage(messages.text(language, "perm_header", mapOf("subject" to subject.name)))
        permissions.forEach { sender.sendMessage(messages.text(language, "perm_entry", mapOf("permission" to it))) }
    }

    private fun resolveSubject(name: String): PermissionSubject {
        val player = players.getByName(name)
        val actualName = player?.username ?: name
        return object : PermissionSubject {
            override val name: String = actualName
        }
    }

    private fun sendUsage(sender: CommandSender, language: String?) {
        sender.sendMessage(messages.text(language, "perm_usage", mapOf("usage" to usage)))
    }
}
