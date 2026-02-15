/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandFlag
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl

class HelpCommandTest {
    @Test
    fun listsOnlyAccessibleCommands() {
        val registry = CommandRegistryImpl()
        val permissions = PermissionCheckerImpl()
        registry.register(SimpleCommand("open", permission = null), "lineage")
        registry.register(SimpleCommand("secure", permission = "lineage.command.secure"), "lineage")

        val help = HelpCommand(registry, permissions)
        val sender = RecordingSender("player", SenderType.PLAYER)
        help.execute(TestContext(sender, "help", emptyList()))

        assertTrue(sender.messages.any { it.contains("/open") })
        assertTrue(sender.messages.none { it.contains("/secure") })
    }

    private data class TestContext(
        override val sender: CommandSender,
        override val input: String,
        override val args: List<String>,
    ) : CommandContext {
        override fun arg(index: Int): String? = args.getOrNull(index)
        override fun hasPermission(permission: String): Boolean = true
    }

    private class RecordingSender(
        override val name: String,
        override val type: SenderType,
    ) : CommandSender {
        val messages = mutableListOf<String>()

        override fun sendMessage(message: String) {
            messages.add(message)
        }
    }

    private class SimpleCommand(
        override val name: String,
        override val permission: String?,
    ) : Command {
        override val aliases: List<String> = emptyList()
        override val description: String = "test"
        override val usage: String = name
        override val flags: Set<CommandFlag> = emptySet()

        override fun execute(context: CommandContext) = Unit

        override fun suggest(context: CommandContext): List<String> = emptyList()
    }
}
