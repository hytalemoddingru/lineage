/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net.handler

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl
import ru.hytalemodding.lineage.proxy.protocol.UpdateLanguagePacketCodec
import ru.hytalemodding.lineage.proxy.session.PlayerSession
import ru.hytalemodding.lineage.proxy.util.Logging
import java.util.UUID

/**
 * Best-effort observer for stream-0 client packets to track runtime language changes.
 *
 * The handler is transparent and does not mutate forwarded frames.
 */
class ClientLanguageUpdateInterceptor(
    private val session: PlayerSession,
    private val playerManager: PlayerManagerImpl,
) : ChannelInboundHandlerAdapter() {
    private val logger = Logging.logger(ClientLanguageUpdateInterceptor::class.java)
    private var cumulation: ByteBuf? = null

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
            observe(msg)
        }
        ctx.fireChannelRead(msg)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        cumulation?.release()
        cumulation = null
    }

    private fun observe(frame: ByteBuf) {
        val chunk = frame.readableBytes()
        if (chunk <= 0) {
            return
        }
        var buffer = cumulation
        if (buffer == null) {
            buffer = Unpooled.buffer(chunk)
        }
        buffer.writeBytes(frame, frame.readerIndex(), chunk)

        while (true) {
            val base = buffer.readerIndex()
            if (buffer.readableBytes() < FRAME_HEADER_SIZE) {
                break
            }
            val payloadLength = buffer.getIntLE(base)
            if (payloadLength <= 0 || payloadLength > MAX_OBSERVED_FRAME_SIZE) {
                resetBuffer(buffer)
                return
            }
            val frameLength = FRAME_HEADER_SIZE + payloadLength
            if (buffer.readableBytes() < frameLength) {
                break
            }
            val packetId = buffer.getIntLE(base + 4)
            if (packetId == UpdateLanguagePacketCodec.PACKET_ID) {
                val payload = buffer.slice(base + FRAME_HEADER_SIZE, payloadLength)
                val language = UpdateLanguagePacketCodec.decodeLanguage(payload)
                if (!language.isNullOrBlank()) {
                    updatePlayerLanguage(language)
                }
            }
            buffer.skipBytes(frameLength)
        }

        if (!buffer.isReadable) {
            buffer.release()
            cumulation = null
            return
        }
        if (buffer.readerIndex() > 0) {
            buffer.discardReadBytes()
        }
        cumulation = buffer
    }

    private fun updatePlayerLanguage(language: String) {
        val playerIdRaw = session.playerId ?: return
        val playerId = runCatching { UUID.fromString(playerIdRaw) }.getOrNull() ?: return
        val player = playerManager.get(playerId) as? ProxyPlayerImpl ?: return
        player.language = language
        logger.debug("Updated player language: {} -> {}", playerId, language)
    }

    private fun resetBuffer(buffer: ByteBuf) {
        runCatching { ReferenceCountUtil.release(buffer) }
        cumulation = null
    }

    private companion object {
        private const val FRAME_HEADER_SIZE = 8
        private const val MAX_OBSERVED_FRAME_SIZE = 1_048_576
    }
}
