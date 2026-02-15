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

class ListPlayersCommandTest {
    @Test
    fun paginatesPlayersAndPrintsNextPageHint() {
        val players = PlayerManagerImpl()
        repeat(55) { index ->
            val player = players.getOrCreate(UUID.randomUUID(), "p$index")
            player.backendId = "hub"
        }
        val command = ListPlayersCommand(players, backendIds = listOf("hub"))
        val sender = RecordingSender("console", SenderType.CONSOLE)

        command.execute(TestContext(sender, "list hub", listOf("hub")))

        assertTrue(sender.messages.any { it.contains("page") && it.contains("1") && it.contains("2") })
        assertTrue(sender.messages.any { it.contains(",") })
        assertTrue(sender.messages.any { it.contains("/list hub 2") })
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
