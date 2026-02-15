/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.protocol

import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class UpdateLanguagePacketCodecTest {
    @Test
    fun decodesLanguageWhenPresent() {
        val payload = encodePayload("ru-RU")
        try {
            val language = UpdateLanguagePacketCodec.decodeLanguage(payload)
            assertEquals("ru-RU", language)
        } finally {
            payload.release()
        }
    }

    @Test
    fun returnsNullWhenLanguageIsMissing() {
        val payload = Unpooled.buffer(1).writeByte(0)
        try {
            val language = UpdateLanguagePacketCodec.decodeLanguage(payload)
            assertNull(language)
        } finally {
            payload.release()
        }
    }

    @Test
    fun rejectsOversizedLanguageField() {
        val payload = Unpooled.buffer()
        payload.writeByte(1)
        VarInt.write(payload, 300)
        repeat(300) { payload.writeByte('a'.code) }
        try {
            val language = UpdateLanguagePacketCodec.decodeLanguage(payload)
            assertNull(language)
        } finally {
            payload.release()
        }
    }

    private fun encodePayload(language: String): io.netty.buffer.ByteBuf {
        val bytes = language.toByteArray(StandardCharsets.UTF_8)
        val payload = Unpooled.buffer(1 + 8 + bytes.size)
        payload.writeByte(1)
        VarInt.write(payload, bytes.size)
        payload.writeBytes(bytes)
        return payload
    }
}
