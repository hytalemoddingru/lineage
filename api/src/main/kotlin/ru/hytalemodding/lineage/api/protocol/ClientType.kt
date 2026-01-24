/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.protocol

/**
 * Client type reported by the Connect packet.
 */
enum class ClientType(val id: Byte) {
    GAME(0),
    EDITOR(1),
    UNKNOWN(-1);

    companion object {
        fun fromId(id: Byte): ClientType {
            return when (id.toInt()) {
                0 -> GAME
                1 -> EDITOR
                else -> UNKNOWN
            }
        }
    }
}
