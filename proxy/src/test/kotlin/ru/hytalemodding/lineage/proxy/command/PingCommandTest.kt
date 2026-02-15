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
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import java.util.UUID

class PingCommandTest {
    @Test
    fun showsUsageForConsoleWhenTargetMissing() {
        val command = PingCommand(PlayerManagerImpl())
        val sender = RecordingSender("console", SenderType.CONSOLE)

        command.execute(TestContext(sender, "ping", emptyList()))

        assertTrue(sender.messages.any { it.contains("Usage", ignoreCase = true) || it.contains("Использование") })
    }

    @Test
    fun returnsUnavailableWhenNoChannelMetadata() {
        val players = PlayerManagerImpl()
        players.getOrCreate(UUID.randomUUID(), "tester")
        val command = PingCommand(players)
        val sender = RecordingSender("console", SenderType.CONSOLE)

        command.execute(TestContext(sender, "ping tester", listOf("tester")))

        assertTrue(sender.messages.any { it.contains("unavailable", ignoreCase = true) || it.contains("недоступ") })
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
}
