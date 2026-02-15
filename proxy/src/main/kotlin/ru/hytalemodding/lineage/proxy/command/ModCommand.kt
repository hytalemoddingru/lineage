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
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader
import ru.hytalemodding.lineage.proxy.mod.ModLoadException
import ru.hytalemodding.lineage.proxy.mod.ModManager

/**
 * Console command for managing loaded mods.
 */
class ModCommand(
    private val modManager: ModManager,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "mod"
    override val aliases: List<String> = listOf("mods")
    override val description: String = "Manage loaded mods"
    override val usage: String = "mod list | mod reload <id|all>"
    override val permission: String? = "lineage.command.mod"
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val language = (sender as? ProxyPlayerCommandSender)?.language
        val args = context.args
        if (args.isEmpty()) {
            sendUsage(sender, language)
            return
        }
        when (args[0].lowercase()) {
            "list" -> sendList(sender, language)
            "reload" -> reload(sender, language, args.getOrNull(1))
            else -> sendUsage(sender, language)
        }
    }

    override fun suggest(context: CommandContext): List<String> {
        val args = context.args
        if (args.isEmpty()) {
            return listOf("list", "reload")
        }
        if (args.size == 1) {
            val prefix = args[0].lowercase()
            return listOf("list", "reload").filter { it.startsWith(prefix) }
        }
        if (args[0].equals("reload", ignoreCase = true) && args.size == 2) {
            val prefix = args[1].lowercase()
            val ids = modManager.all().map { it.info.id }
            return (ids + "all").filter { it.lowercase().startsWith(prefix) }
        }
        return emptyList()
    }

    private fun sendList(sender: CommandSender, language: String?) {
        val mods = modManager.all()
        if (mods.isEmpty()) {
            sender.sendMessage(messages.text(language, "mod_no_mods"))
            return
        }
        sender.sendMessage(messages.text(language, "mod_loaded_header", mapOf("count" to mods.size.toString())))
        for (mod in mods) {
            sender.sendMessage(messages.text(
                language,
                "mod_loaded_entry",
                mapOf(
                    "id" to mod.info.id,
                    "version" to mod.info.version,
                    "state" to mod.state.name.lowercase(),
                ),
            ))
        }
    }

    private fun reload(sender: CommandSender, language: String?, target: String?) {
        if (target == null) {
            sender.sendMessage(messages.text(language, "mod_reload_usage"))
            return
        }
        try {
            if (target.equals("all", ignoreCase = true)) {
                modManager.reloadAll()
                sender.sendMessage(messages.text(language, "mod_reloaded_all"))
            } else {
                modManager.reload(target)
                sender.sendMessage(messages.text(language, "mod_reloaded_one", mapOf("id" to target)))
            }
        } catch (ex: ModLoadException) {
            sender.sendMessage(ex.message ?: messages.text(language, "mod_reload_failed"))
        }
    }

    private fun sendUsage(sender: CommandSender, language: String?) {
        sender.sendMessage(messages.text(language, "mod_usage", mapOf("usage" to usage)))
    }
}
