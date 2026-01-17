/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.messaging

import ru.hytalemodding.lineage.api.messaging.Channel
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import java.util.concurrent.ConcurrentHashMap

/**
 * Messaging implementation that drops all traffic.
 */
class NoopMessaging : Messaging {
    private val channels = ConcurrentHashMap<String, Channel>()

    override fun registerChannel(id: String, handler: MessageHandler): Channel {
        val channel = NoopChannel(id)
        val existing = channels.putIfAbsent(id, channel)
        if (existing != null) {
            throw IllegalArgumentException("Channel already registered: $id")
        }
        return channel
    }

    override fun unregisterChannel(id: String) {
        channels.remove(id)
    }

    override fun channel(id: String): Channel? = channels[id]

    private class NoopChannel(
        override val id: String,
    ) : Channel {
        override fun send(payload: ByteArray) {
        }
    }
}
