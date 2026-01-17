/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.messaging.Channel
import ru.hytalemodding.lineage.api.messaging.Message
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol
import java.util.UUID

class PlayerCommandGatewayTest {
    @Test
    fun dispatchesPlayerCommandAndSendsResponse() {
        val messaging = TestMessaging()
        val registry = CommandRegistryImpl()
        val dispatcher = CommandDispatcher(registry, PermissionCheckerImpl())
        val players = PlayerManagerImpl()
        val playerId = UUID.randomUUID()
        players.getOrCreate(playerId, "tester")

        registry.register(SimpleCommand("ping", "pong"))

        PlayerCommandGateway(messaging, dispatcher, players)

        val payload = PlayerCommandProtocol.encodeRequest(playerId, "ping")
        messaging.receive(PlayerCommandProtocol.REQUEST_CHANNEL_ID, payload)

        val responses = messaging.sentPayloads(PlayerCommandProtocol.RESPONSE_CHANNEL_ID)
        val decoded = PlayerCommandProtocol.decodeResponse(responses.first())

        assertNotNull(decoded)
        decoded!!
        assertEquals(playerId, decoded.playerId)
        assertEquals("pong", decoded.message)
    }

    private class SimpleCommand(
        override val name: String,
        private val response: String,
    ) : Command {
        override val aliases: List<String> = emptyList()
        override val description: String = "test"
        override val permission: String? = null

        override fun execute(context: CommandContext) {
            context.sender.sendMessage(response)
        }

        override fun suggest(context: CommandContext): List<String> = emptyList()
    }

    private class TestMessaging : Messaging {
        private val entries = mutableMapOf<String, Entry>()

        override fun registerChannel(id: String, handler: MessageHandler): Channel {
            if (entries.containsKey(id)) {
                throw IllegalArgumentException("Channel already registered: $id")
            }
            val channel = TestChannel(id)
            entries[id] = Entry(channel, handler)
            return channel
        }

        override fun unregisterChannel(id: String) {
            entries.remove(id)
        }

        override fun channel(id: String): Channel? = entries[id]?.channel

        fun receive(id: String, payload: ByteArray) {
            val entry = entries[id] ?: return
            entry.handler.onMessage(Message(id, payload, null))
        }

        fun sentPayloads(id: String): List<ByteArray> {
            return entries[id]?.channel?.sent ?: emptyList()
        }
    }

    private data class Entry(
        val channel: TestChannel,
        val handler: MessageHandler,
    )

    private class TestChannel(
        override val id: String,
    ) : Channel {
        val sent = mutableListOf<ByteArray>()

        override fun send(payload: ByteArray) {
            sent.add(payload)
        }
    }
}
