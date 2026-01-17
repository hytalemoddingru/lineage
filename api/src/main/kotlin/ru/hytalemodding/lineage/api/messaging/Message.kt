/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.messaging

/**
 * Raw message payload delivered over a channel.
 */
data class Message(
    val channelId: String,
    val payload: ByteArray,
    val sender: MessageSender? = null,
)
