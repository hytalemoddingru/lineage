/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.messaging

/**
 * Encodes and decodes typed messages for a channel.
 */
interface Codec<T> {
    fun encode(value: T): ByteArray
    fun decode(payload: ByteArray): T
}
