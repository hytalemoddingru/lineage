/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import ru.hytalemodding.lineage.api.player.PlayerState
import ru.hytalemodding.lineage.api.player.ProxyPlayer
import java.util.UUID

/**
 * Mutable proxy player implementation.
 */
class ProxyPlayerImpl(
    override val id: UUID,
    override var username: String,
    private val transferServiceProvider: () -> PlayerTransferService?,
) : ProxyPlayer {
    override var state: PlayerState = PlayerState.CONNECTING
    override var backendId: String? = null

    override fun transferTo(backendId: String) {
        val service = transferServiceProvider()
        if (service == null || !service.requestTransfer(this, backendId)) {
            applyTransfer(backendId)
        }
    }

    override fun disconnect(reason: String?) {
        this.state = PlayerState.DISCONNECTED
    }

    override fun sendMessage(message: String) {
        // Placeholder: hook into client stream once message protocol is exposed.
    }

    internal fun applyTransfer(targetBackendId: String) {
        backendId = targetBackendId
        state = PlayerState.TRANSFERRING
    }
}
