/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.messaging

/**
 * Helpers for registering typed messaging channels.
 */
object MessagingChannels {
    @JvmStatic
    fun <T> registerTyped(
        messaging: Messaging,
        id: String,
        codec: Codec<T>,
        handler: TypedMessageHandler<T>,
    ): TypedChannel<T> {
        val channel = messaging.registerChannel(id) { message ->
            val decoded = codec.decode(message.payload)
            handler.onMessage(
                TypedMessage(
                    channelId = message.channelId,
                    payload = decoded,
                    sender = message.sender,
                ),
            )
        }
        return TypedChannel(channel, codec)
    }

    @JvmStatic
    fun <T> typed(channel: Channel, codec: Codec<T>): TypedChannel<T> {
        return TypedChannel(channel, codec)
    }
}
