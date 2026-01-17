/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net.handler

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
/**
 * Decodes Hytale frames that use a 4-byte little-endian payload length prefix
 * followed by a 4-byte packet ID.
 */
class VarIntFrameDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        if (`in`.readableBytes() < 8) return

        `in`.markReaderIndex()
        val payloadLength = `in`.readIntLE()
        val totalLength = 8 + payloadLength
        if (`in`.readableBytes() < totalLength - 4) {
            `in`.resetReaderIndex()
            return
        }

        `in`.resetReaderIndex()
        out.add(`in`.readRetainedSlice(totalLength))
    }
}
