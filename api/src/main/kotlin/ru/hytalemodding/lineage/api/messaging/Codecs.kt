/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.messaging

/**
 * Standard codecs for messaging channels.
 */
object Codecs {
    @JvmField
    val UTF8_STRING: Codec<String> = object : Codec<String> {
        override fun encode(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)

        override fun decode(payload: ByteArray): String = payload.toString(Charsets.UTF_8)
    }
}
