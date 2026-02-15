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
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl

class HelpCommand(
    private val registry: CommandRegistryImpl,
    private val permissionChecker: PermissionCheckerImpl,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "help"
    override val aliases: List<String> = listOf("?", "помощь", "sos")
    override val description: String = "Show available commands"
    override val usage: String = "help [command]"
    override val permission: String? = null
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val language = (sender as? ProxyPlayerCommandSender)?.language
        val commandName = context.args.firstOrNull()?.trim()

        if (commandName.isNullOrEmpty()) {
            val available = availableEntries(context)
            if (available.isEmpty()) {
                sender.sendMessage(messages.text(language, "help_no_commands"))
                return
            }
            val names = available.joinToString(", ") { "/${it.baseNames.first()}" }
            sender.sendMessage(
                listOf(
                    messages.text(language, "help_header", mapOf("count" to available.size.toString())),
                    messages.text(language, "help_line_commands", mapOf("commands" to names)),
                    messages.text(language, "help_usage", mapOf("usage" to usage)),
                ).joinToString("\n")
            )
            return
        }

        val entry = resolveEntry(commandName)
        if (entry == null || !isAvailable(context, entry)) {
            sender.sendMessage(messages.text(language, "help_command_not_found", mapOf("command" to commandName)))
            return
        }

        val command = entry.command
        val aliases = entry.baseNames.drop(1).joinToString(", ").ifBlank { "-" }
        val permission = command.permission ?: "-"
        val lines = mutableListOf(
            messages.text(language, "help_detail_header", mapOf("name" to entry.baseNames.first())),
            messages.text(language, "help_detail_description", mapOf("value" to command.description)),
            messages.text(language, "help_detail_usage", mapOf("value" to command.usage)),
            messages.text(language, "help_detail_aliases", mapOf("value" to aliases)),
            messages.text(language, "help_detail_permission", mapOf("value" to permission)),
        )
        if (CommandFlag.PLAYER_ONLY in command.flags) {
            lines.add(messages.text(language, "help_detail_player_only"))
        }
        sender.sendMessage(lines.joinToString("\n"))
    }

    override fun suggest(context: CommandContext): List<String> {
        if (context.args.size > 1) {
            return emptyList()
        }
        val query = context.args.firstOrNull()?.trim()?.lowercase().orEmpty()
        return availableEntries(context)
            .map { it.baseNames.first() }
            .distinct()
            .sorted()
            .filter { it.lowercase().startsWith(query) }
    }

    private fun availableEntries(context: CommandContext): List<CommandEntry> {
        return registry.snapshot()
            .asSequence()
            .filter { isAvailable(context, it) }
            .sortedBy { it.baseNames.first() }
            .toList()
    }

    private fun isAvailable(context: CommandContext, entry: CommandEntry): Boolean {
        val command = entry.command
        if (CommandFlag.HIDDEN in command.flags) {
            return false
        }
        if (CommandFlag.PLAYER_ONLY in command.flags && context.sender.type != SenderType.PLAYER) {
            return false
        }
        val permission = command.permission
        if (permission != null && !permissionChecker.hasPermission(context.sender, permission)) {
            return false
        }
        return true
    }

    private fun resolveEntry(name: String): CommandEntry? {
        val command = registry.get(name) ?: return null
        return registry.snapshot().firstOrNull { it.command == command }
    }
}
