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
import org.junit.jupiter.api.Test

class VarIntTest {
    @Test
    fun writeReadRoundTrip() {
        val values = listOf(0, 1, 2, 127, 128, 255, 16_384, 2_097_151, 268_435_455)
        val buffer = Unpooled.buffer()

        for (value in values) {
            buffer.clear()
            VarInt.write(buffer, value)
            buffer.readerIndex(0)
            assertEquals(value, VarInt.read(buffer))
        }
    }

    @Test
    fun peekAndLengthMatchSize() {
        val buffer = Unpooled.buffer()
        VarInt.write(buffer, 300)

        assertEquals(300, VarInt.peek(buffer, 0))
        assertEquals(VarInt.size(300), VarInt.length(buffer, 0))
    }

    @Test
    fun peekAndLengthReturnMinusOneForIncomplete() {
        val buffer = Unpooled.buffer()
        buffer.writeByte(0x80)

        assertEquals(-1, VarInt.peek(buffer, 0))
        assertEquals(-1, VarInt.length(buffer, 0))
    }
}
