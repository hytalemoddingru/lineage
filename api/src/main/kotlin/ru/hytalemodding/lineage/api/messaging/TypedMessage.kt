/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.messaging

/**
 * Typed message payload delivered over a channel.
 */
data class TypedMessage<T>(
    val channelId: String,
    val payload: T,
    val sender: MessageSender? = null,
)
