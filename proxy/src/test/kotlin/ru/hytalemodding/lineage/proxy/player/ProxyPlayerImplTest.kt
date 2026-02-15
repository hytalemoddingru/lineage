/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ProxyPlayerImplTest {
    @Test
    fun sendMessageDelegatesToConfiguredSender() {
        val playerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val delivered = mutableListOf<Pair<ProxyPlayerImpl, String>>()
        val sender: (ProxyPlayerImpl, String) -> Boolean = { player, message ->
            delivered.add(player to message)
            true
        }
        val player = ProxyPlayerImpl(playerId, "tester", { null }) { sender }

        player.sendMessage("hello")

        assertEquals(playerId, delivered.single().first.id)
        assertEquals("hello", delivered.single().second)
    }

    @Test
    fun sendMessageUsesLatestSenderProviderState() {
        val playerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val delivered = mutableListOf<String>()
        var sender: (ProxyPlayerImpl, String) -> Boolean = { _, _ -> false }
        val player = ProxyPlayerImpl(playerId, "tester", { null }) { sender }

        player.sendMessage("first")
        sender = { _, message ->
            delivered.add(message)
            true
        }
        player.sendMessage("second")

        assertEquals(listOf("second"), delivered)
    }
}
