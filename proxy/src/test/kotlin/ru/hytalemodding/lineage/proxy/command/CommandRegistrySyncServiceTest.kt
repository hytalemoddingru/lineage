/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandFlag
import ru.hytalemodding.lineage.api.messaging.Channel
import ru.hytalemodding.lineage.api.messaging.Message
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.shared.command.ProxyCommandRegistryProtocol

class CommandRegistrySyncServiceTest {
    @Test
    fun sendsSnapshotOnValidRequest() {
        val messaging = TestMessaging()
        val registry = CommandRegistryImpl()
        registry.register(SimpleCommand("ping"), "lineage")
        val service = CommandRegistrySyncService(registry, messaging)

        val payload = ProxyCommandRegistryProtocol.encodeRequest()
        messaging.receive(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, payload)

        val snapshots = messaging.sentPayloads(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID)
        assertEquals(1, snapshots.size)
        assertTrue(ProxyCommandRegistryProtocol.decodeSnapshot(snapshots.first()) != null)
        assertTrue(service.rejectCountersSnapshot().isEmpty())
    }

    @Test
    fun rejectsRequestWithUnsupportedVersion() {
        val messaging = TestMessaging()
        val registry = CommandRegistryImpl()
        registry.register(SimpleCommand("ping"), "lineage")
        val service = CommandRegistrySyncService(registry, messaging)

        val payload = ProxyCommandRegistryProtocol.encodeRequest().clone()
        payload[0] = 1
        messaging.receive(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, payload)

        val snapshots = messaging.sentPayloads(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID)
        assertTrue(snapshots.isEmpty())
        assertEquals(1L, service.rejectCountersSnapshot()["VERSION_MISMATCH"])
    }

    @Test
    fun rejectsReplayedRequest() {
        val messaging = TestMessaging()
        val registry = CommandRegistryImpl()
        registry.register(SimpleCommand("ping"), "lineage")
        val service = CommandRegistrySyncService(registry, messaging)

        val payload = ProxyCommandRegistryProtocol.encodeRequest()
        messaging.receive(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, payload)
        messaging.receive(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, payload)

        val snapshots = messaging.sentPayloads(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID)
        assertEquals(1, snapshots.size)
        assertEquals(1L, service.rejectCountersSnapshot()["REPLAYED_REQUEST"])
    }

    private class SimpleCommand(
        override val name: String,
    ) : Command {
        override val aliases: List<String> = emptyList()
        override val description: String = "test"
        override val usage: String = name
        override val permission: String? = null
        override val flags: Set<CommandFlag> = emptySet()

        override fun execute(context: CommandContext) = Unit

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
