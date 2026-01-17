/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.messaging

/**
 * Typed view over a messaging channel.
 */
class TypedChannel<T>(
    private val channel: Channel,
    private val codec: Codec<T>,
) {
    val id: String
        get() = channel.id

    fun send(value: T) {
        channel.send(codec.encode(value))
    }
}
