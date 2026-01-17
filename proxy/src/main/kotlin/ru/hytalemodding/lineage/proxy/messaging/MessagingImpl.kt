/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.messaging

import ru.hytalemodding.lineage.api.messaging.Channel
import ru.hytalemodding.lineage.api.messaging.Message
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.MessageSender
import ru.hytalemodding.lineage.api.messaging.Messaging
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Messaging implementation backed by a UDP server.
 */
class MessagingImpl(
    private val server: MessagingServer,
) : Messaging {
    private val channels = ConcurrentHashMap<String, ChannelEntry>()

    override fun registerChannel(id: String, handler: MessageHandler): Channel {
        validateChannelId(id)
        val entry = ChannelEntry(id, handler)
        val existing = channels.putIfAbsent(id, entry)
        if (existing != null) {
            throw IllegalArgumentException("Channel already registered: $id")
        }
        return entry
    }

    override fun unregisterChannel(id: String) {
        channels.remove(id)
    }

    override fun channel(id: String): Channel? = channels[id]

    fun start() {
        server.start()
    }

    fun onPacket(address: SocketAddress, channelId: String, payload: ByteArray) {
        val entry = channels[channelId] ?: return
        val sender = MessageSender(address)
        entry.handler.onMessage(Message(channelId, payload, sender))
    }

    private fun validateChannelId(id: String) {
        val normalized = id.trim()
        require(normalized.isNotEmpty()) { "Channel id must not be blank" }
        require(normalized.length <= 64) { "Channel id must be <= 64 characters" }
        require(CHANNEL_ID_REGEX.matches(normalized)) { "Channel id has invalid characters: $id" }
    }

    private inner class ChannelEntry(
        override val id: String,
        val handler: MessageHandler,
    ) : Channel {
        override fun send(payload: ByteArray) {
            server.broadcast(id, payload)
        }
    }

    private companion object {
        private val CHANNEL_ID_REGEX = Regex("^[A-Za-z0-9_.:-]+$")
    }
}
