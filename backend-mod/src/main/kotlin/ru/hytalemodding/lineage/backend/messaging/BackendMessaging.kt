/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.messaging

import java.net.InetSocketAddress

/**
 * Facade for backend messaging client.
 */
object BackendMessaging {
    private var client: BackendMessagingClient? = null

    fun start(address: InetSocketAddress, secret: ByteArray) {
        if (client != null) {
            return
        }
        val newClient = BackendMessagingClient(address, secret)
        newClient.start()
        client = newClient
    }

    fun stop() {
        client?.close()
        client = null
    }

    fun registerChannel(id: String, handler: (ByteArray) -> Unit) {
        client?.registerChannel(id, handler)
    }

    fun unregisterChannel(id: String) {
        client?.unregisterChannel(id)
    }

    fun send(channelId: String, payload: ByteArray) {
        client?.send(channelId, payload)
    }
}
