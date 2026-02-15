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
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType

class StopCommandTest {
    @Test
    fun consoleCanRequestShutdown() {
        var requested = false
        val command = StopCommand(requestShutdown = { requested = true })
        val sender = RecordingSender("console", SenderType.CONSOLE)

        command.execute(TestContext(sender))

        assertTrue(requested)
        assertTrue(sender.messages.any { it.contains("Stopping Lineage proxy") })
    }

    @Test
    fun playerCannotRequestShutdown() {
        var requested = false
        val command = StopCommand(requestShutdown = { requested = true })
        val sender = RecordingSender("player", SenderType.PLAYER)

        command.execute(TestContext(sender))

        assertFalse(requested)
        assertTrue(sender.messages.any { it.contains("only available from console", ignoreCase = true) })
    }

    private data class TestContext(
        override val sender: CommandSender,
        override val input: String = "stop",
        override val args: List<String> = emptyList(),
    ) : CommandContext {
        override fun arg(index: Int): String? = args.getOrNull(index)
        override fun hasPermission(permission: String): Boolean = true
    }

    private class RecordingSender(
        override val name: String,
        override val type: SenderType,
    ) : CommandSender {
        val messages: MutableList<String> = mutableListOf()

        override fun sendMessage(message: String) {
            messages.add(message)
        }
    }
}
