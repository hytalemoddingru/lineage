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
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader

/**
 * Parses and dispatches command input.
 */
class CommandDispatcher(
    private val registry: CommandRegistry,
    private val permissionChecker: PermissionChecker,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) {
    private val logger = ru.hytalemodding.lineage.proxy.util.Logging.logger(CommandDispatcher::class.java)

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
        val senderLanguage = (sender as? ProxyPlayerCommandSender)?.language
        if (CommandFlag.PLAYER_ONLY in command.flags && sender.type != SenderType.PLAYER) {
            sender.sendMessage(messages.text(senderLanguage, "dispatcher_player_only"))
            return true
        }
        val permission = command.permission
        if (permission != null && !context.hasPermission(permission)) {
            sender.sendMessage(messages.text(senderLanguage, "dispatcher_no_permission"))
            return true
        }
        loggerCommand(sender, trimmed)
        command.execute(context)
        return true
    }

    fun complete(sender: CommandSender, input: String): List<String> {
        val endsWithWhitespace = input.isNotEmpty() && input.last().isWhitespace()
        val tokens = input.trimStart()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            return commandCandidates("")
        }
        if (tokens.size == 1 && !endsWithWhitespace) {
            return commandCandidates(tokens[0])
        }

        val command = registry.get(tokens[0]) ?: return commandCandidates(tokens[0])
        val args = mutableListOf<String>()
        if (tokens.size > 1) {
            args.addAll(tokens.drop(1))
        }
        if (endsWithWhitespace) {
            args.add("")
        }
        val context = CommandContextImpl(
            sender = sender,
            input = input,
            args = args,
            permissionChecker = permissionChecker,
        )
        val currentArg = args.lastOrNull()?.lowercase().orEmpty()
        val suggestions = runCatching { command.suggest(context) }.getOrElse { emptyList() }
        return suggestions
            .asSequence()
            .filter { it.isNotBlank() }
            .filter { it.lowercase().startsWith(currentArg) }
            .distinct()
            .sorted()
            .toList()
    }

    private fun commandCandidates(prefix: String): List<String> {
        val normalized = prefix.trim().lowercase()
        val names = (registry as? CommandRegistryImpl)
            ?.snapshot()
            ?.asSequence()
            ?.flatMap { entry -> entry.baseNames.asSequence() }
            ?.distinct()
            ?.sorted()
            ?.toList()
            ?: emptyList()
        if (normalized.isEmpty()) {
            return names
        }
        return names.filter { it.startsWith(normalized) }
    }

    private fun loggerCommand(sender: CommandSender, input: String) {
        val commandText = "/$input"
        if (sender is ProxyPlayerCommandSender) {
            logger.info("{} issued command [{}]", sender.name, commandText)
            return
        }
        logger.info("{} issued command [{}]", sender.name, commandText)
    }
}
