/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.proxy.event.player.PlayerTransferEvent

/**
 * Coordinates transfer state and emits events.
 */
class PlayerTransferService(
    private val eventBus: EventBus,
) {
    fun transfer(player: ProxyPlayerImpl, targetBackendId: String) {
        val previous = player.backendId
        if (previous == targetBackendId) {
            return
        }
        player.transferTo(targetBackendId)
        eventBus.post(PlayerTransferEvent(player, previous, targetBackendId))
    }
}
