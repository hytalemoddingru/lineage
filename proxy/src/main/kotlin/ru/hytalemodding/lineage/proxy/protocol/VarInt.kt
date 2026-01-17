/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.protocol

import io.netty.buffer.ByteBuf

/**
 * Variable-length integer encoding used by the Hytale protocol.
 */
object VarInt {
    /**
     * Writes [value] as a VarInt into [buf].
     */
    fun write(buf: ByteBuf, value: Int) {
        require(value >= 0) { "VarInt cannot encode negative values: $value" }
        var current = value
        while ((current and 0xFFFFFF80.toInt()) != 0) {
            buf.writeByte(current and 0x7F or 0x80)
            current = current ushr 7
        }
        buf.writeByte(current)
    }

    /**
     * Reads a VarInt from [buf].
     */
    fun read(buf: ByteBuf): Int {
        var value = 0
        var shift = 0
        do {
            val b = buf.readByte().toInt()
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) {
                return value
            }
            shift += 7
        } while (shift <= 28)
        throw IllegalArgumentException("VarInt exceeds maximum length (5 bytes)")
    }

    /**
     * Peeks a VarInt value at [index] without changing reader index.
     */
    fun peek(buf: ByteBuf, index: Int): Int {
        var value = 0
        var shift = 0
        var pos = index
        while (pos < buf.writerIndex()) {
            val b = buf.getByte(pos++).toInt()
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) {
                return value
            }
            shift += 7
            if (shift > 28) {
                return -1
            }
        }
        return -1
    }

    /**
     * Returns VarInt byte length at [index], or -1 if incomplete.
     */
    fun length(buf: ByteBuf, index: Int): Int {
        var pos = index
        while (pos < buf.writerIndex()) {
            if (buf.getByte(pos++).toInt() and 0x80 == 0) {
                return pos - index
            }
            if (pos - index > 5) {
                return -1
            }
        }
        return -1
    }

    /**
     * Computes encoded size for [value].
     */
    fun size(value: Int): Int {
        if (value and 0xFFFFFF80.toInt() == 0) return 1
        if (value and 0xFFFFC000.toInt() == 0) return 2
        if (value and 0xFFE00000.toInt() == 0) return 3
        if (value and 0xF0000000.toInt() == 0) return 4
        return 5
    }
}
