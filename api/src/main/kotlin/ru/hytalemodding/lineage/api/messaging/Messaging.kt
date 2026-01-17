/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.messaging

/**
 * Manages messaging channels for proxy communication.
 */
interface Messaging {
    fun registerChannel(id: String, handler: MessageHandler): Channel
    fun unregisterChannel(id: String)
    fun channel(id: String): Channel?
}
