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
import ru.hytalemodding.lineage.proxy.i18n.LocalizationRuntime
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader

class MessagesCommand(
    private val localizationRuntime: LocalizationRuntime,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "messages"
    override val aliases: List<String> = listOf("locale", "lang")
    override val description: String = "Reload localized messages and text rendering style"
    override val usage: String = "messages reload"
    override val permission: String? = "lineage.command.messages"
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val language = (sender as? ProxyPlayerCommandSender)?.language
        val action = context.args.firstOrNull()?.lowercase()
        if (action != "reload") {
            sender.sendMessage(messages.text(language, "messages_usage", mapOf("usage" to usage)))
            return
        }

        val result = localizationRuntime.reload()
        if (result.success) {
            sender.sendMessage(messages.text(language, "messages_reload_ok"))
            return
        }

        val details = result.errors.joinToString(" | ")
        sender.sendMessage(messages.text(language, "messages_reload_partial", mapOf("details" to details)))
    }

    override fun suggest(context: CommandContext): List<String> {
        val args = context.args
        if (args.isEmpty()) {
            return listOf("reload")
        }
        if (args.size == 1) {
            return listOf("reload").filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }
}
