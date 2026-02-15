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

class StopCommand(
    private val requestShutdown: () -> Unit,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "stop"
    override val aliases: List<String> = listOf("exit", "end")
    override val description: String = "Stop Lineage proxy gracefully"
    override val usage: String = "stop"
    override val permission: String? = "lineage.command.stop"
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val language = (sender as? ProxyPlayerCommandSender)?.language
        if (sender.type != SenderType.CONSOLE) {
            sender.sendMessage(messages.text(language, "stop_console_only"))
            return
        }
        sender.sendMessage(messages.text(language, "stop_stopping"))
        requestShutdown()
    }

    override fun suggest(context: CommandContext): List<String> = emptyList()
}
