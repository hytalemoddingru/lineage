/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.protocol

import io.netty.buffer.ByteBuf
import java.nio.charset.StandardCharsets

/**
 * Decodes `UpdateLanguage` packet payload.
 *
 * Packet id (frame-level): 232
 * Payload format:
 * - null bits: byte
 * - language: nullable var-string
 */
object UpdateLanguagePacketCodec {
    const val PACKET_ID: Int = 232
    private const val LANGUAGE_PRESENT_BIT: Int = 0x01
    private const val MAX_LANGUAGE_BYTES: Int = 256

    fun decodeLanguage(payload: ByteBuf): String? {
        if (!payload.isReadable) {
            return null
        }
        val base = payload.readerIndex()
        val limit = payload.writerIndex()
        if (base >= limit) {
            return null
        }
        val nullBits = payload.getByte(base).toInt()
        if ((nullBits and LANGUAGE_PRESENT_BIT) == 0) {
            return null
        }
        var offset = base + 1
        val length = VarInt.peek(payload, offset)
        if (length < 0 || length > MAX_LANGUAGE_BYTES) {
            return null
        }
        val varIntLength = VarInt.length(payload, offset)
        if (varIntLength <= 0) {
            return null
        }
        offset += varIntLength
        if (offset + length > limit) {
            return null
        }
        if (length == 0) {
            return null
        }
        val bytes = ByteArray(length)
        payload.getBytes(offset, bytes)
        return String(bytes, StandardCharsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }
}
