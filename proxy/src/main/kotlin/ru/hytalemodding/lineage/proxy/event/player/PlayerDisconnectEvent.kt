/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.event.player

import ru.hytalemodding.lineage.api.event.Event
import ru.hytalemodding.lineage.api.player.ProxyPlayer

/**
 * Emitted when a player disconnects from the proxy.
 */
data class PlayerDisconnectEvent(
    val player: ProxyPlayer,
    val reason: String? = null,
) : Event
