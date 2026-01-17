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
 * Host and port pair encoded in the Hytale referral format.
 */
data class HostAddress(
    val host: String,
    val port: Int,
) {
    /**
     * Writes the address to [buf] using little-endian port and length-prefixed host.
     */
    fun serialize(buf: ByteBuf) {
        buf.writeShortLE(port)
        val bytes = host.toByteArray(StandardCharsets.UTF_8)
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }
}
