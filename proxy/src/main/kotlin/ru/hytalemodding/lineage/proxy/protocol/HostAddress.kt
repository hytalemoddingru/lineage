/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.protocol

import io.netty.buffer.ByteBuf
import ru.hytalemodding.lineage.shared.protocol.ProtocolLimits
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
        val bytes = host.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= ProtocolLimits.MAX_HOST_LENGTH) {
            "Host exceeds max length ${ProtocolLimits.MAX_HOST_LENGTH}"
        }
        buf.writeShortLE(port)
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    companion object {
        fun validateStructure(buf: ByteBuf, offset: Int, limit: Int, maxHostLength: Int = ProtocolLimits.MAX_HOST_LENGTH): Boolean {
            if (offset < 0 || offset + 2 > limit) return false
            val pos = offset + 2
            val hostLen = VarInt.peek(buf, pos)
            if (hostLen < 0 || hostLen > maxHostLength) return false
            val varIntLen = VarInt.length(buf, pos)
            if (varIntLen <= 0) return false
            return pos + varIntLen + hostLen <= limit
        }
    }
}
