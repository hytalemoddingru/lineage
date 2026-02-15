/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandFlag
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl

class CommandDispatcherTest {
    @Test
    fun dispatchReturnsFalseWhenUnknown() {
        val dispatcher = CommandDispatcher(CommandRegistryImpl(), PermissionCheckerImpl())
        val sender = RecordingSender("player")
        assertFalse(dispatcher.dispatch(sender, "missing"))
    }

    @Test
    fun deniesCommandWithoutPermission() {
        val registry = CommandRegistryImpl()
        val permissionChecker = PermissionCheckerImpl()
        val dispatcher = CommandDispatcher(registry, permissionChecker)
        val sender = RecordingSender("player")
        val command = TestCommand("ping", listOf("p"), "perm.use")
        registry.register(command)

        assertTrue(dispatcher.dispatch(sender, "ping"))
        assertTrue(sender.messages.any { it.contains("You do not have permission to run this command.") })
        assertFalse(command.executed)
    }

    @Test
    fun executesWhenPermissionGranted() {
        val registry = CommandRegistryImpl()
        val permissionChecker = PermissionCheckerImpl()
        val dispatcher = CommandDispatcher(registry, permissionChecker)
        val sender = RecordingSender("player")
        val command = TestCommand("ping", listOf("p"), "perm.use")
        registry.register(command)
        permissionChecker.grant(sender, "perm.use")

        assertTrue(dispatcher.dispatch(sender, "p"))
        assertTrue(command.executed)
    }

    @Test
    fun blocksPlayerOnlyFromConsole() {
        val registry = CommandRegistryImpl()
        val dispatcher = CommandDispatcher(registry, PermissionCheckerImpl())
        val sender = RecordingSender("console", SenderType.CONSOLE)
        val command = TestCommand("ping", emptyList(), null, setOf(CommandFlag.PLAYER_ONLY))
        registry.register(command)

        assertTrue(dispatcher.dispatch(sender, "ping"))
        assertTrue(sender.messages.any { it.contains("Command is only available to players.") })
        assertFalse(command.executed)
    }

    private class TestCommand(
        override val name: String,
        override val aliases: List<String>,
        override val permission: String?,
        override val flags: Set<CommandFlag> = emptySet(),
    ) : Command {
        override val description: String = "test"
        override val usage: String = "ping"
        var executed: Boolean = false

        override fun execute(context: CommandContext) {
            executed = true
        }

        override fun suggest(context: CommandContext): List<String> = emptyList()
    }

    private class RecordingSender(
        override val name: String,
        override val type: SenderType = SenderType.PLAYER,
    ) : CommandSender {
        val messages = mutableListOf<String>()

        override fun sendMessage(message: String) {
            messages.add(message)
        }
    }
}
