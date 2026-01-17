/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.player

import java.util.UUID

/**
 * Represents a player connected to the proxy.
 */
interface ProxyPlayer {
    val id: UUID
    val username: String
    val state: PlayerState
    val backendId: String?

    fun transferTo(backendId: String)
    fun disconnect(reason: String? = null)
    fun sendMessage(message: String)
}
