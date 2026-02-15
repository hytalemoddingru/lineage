/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.command

import ru.hytalemodding.lineage.backend.messaging.BackendMessaging

internal interface BackendBridgeMessaging {
    fun registerChannel(id: String, handler: (ByteArray) -> Unit)
    fun unregisterChannel(id: String)
    fun send(channelId: String, payload: ByteArray)
}

internal object DefaultBackendBridgeMessaging : BackendBridgeMessaging {
    override fun registerChannel(id: String, handler: (ByteArray) -> Unit) {
        BackendMessaging.registerChannel(id, handler)
    }

    override fun unregisterChannel(id: String) {
        BackendMessaging.unregisterChannel(id)
    }

    override fun send(channelId: String, payload: ByteArray) {
        BackendMessaging.send(channelId, payload)
    }
}
