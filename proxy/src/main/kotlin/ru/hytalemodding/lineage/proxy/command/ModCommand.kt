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
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.proxy.mod.ModLoadException
import ru.hytalemodding.lineage.proxy.mod.ModManager

/**
 * Console command for managing loaded mods.
 */
class ModCommand(
    private val modManager: ModManager,
) : Command {
    override val name: String = "mod"
    override val aliases: List<String> = listOf("mods")
    override val description: String = "Manage loaded mods"
    override val permission: String? = "lineage.command.mod"

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val args = context.args
        if (args.isEmpty()) {
            sendUsage(sender)
            return
        }
        when (args[0].lowercase()) {
            "list" -> sendList(sender)
            "reload" -> reload(sender, args.getOrNull(1))
            else -> sendUsage(sender)
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

    private fun sendList(sender: CommandSender) {
        val mods = modManager.all()
        if (mods.isEmpty()) {
            sender.sendMessage("No mods loaded.")
            return
        }
        sender.sendMessage("Loaded mods: ${mods.size}")
        for (mod in mods) {
            sender.sendMessage(
                "- ${mod.info.id} ${mod.info.version} (${mod.state.name.lowercase()})",
            )
        }
    }

    private fun reload(sender: CommandSender, target: String?) {
        if (target == null) {
            sender.sendMessage("Usage: mod reload <id|all>")
            return
        }
        try {
            if (target.equals("all", ignoreCase = true)) {
                modManager.reloadAll()
                sender.sendMessage("Reloaded all mods.")
            } else {
                modManager.reload(target)
                sender.sendMessage("Reloaded mod $target.")
            }
        } catch (ex: ModLoadException) {
            sender.sendMessage(ex.message ?: "Failed to reload mod.")
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("Usage: mod list | mod reload <id|all>")
    }
}
