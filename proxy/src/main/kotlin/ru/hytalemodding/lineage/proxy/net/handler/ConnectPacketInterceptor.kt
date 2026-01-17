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
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.util.ReferenceCountUtil
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.player.PlayerState
import ru.hytalemodding.lineage.proxy.event.player.PlayerConnectEvent
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.player.PlayerTransferService
import ru.hytalemodding.lineage.proxy.protocol.CONNECT_PACKET_ID
import ru.hytalemodding.lineage.proxy.protocol.ConnectPacketCodec
import ru.hytalemodding.lineage.proxy.protocol.HostAddress
import ru.hytalemodding.lineage.proxy.routing.Router
import ru.hytalemodding.lineage.proxy.security.TokenService
import ru.hytalemodding.lineage.proxy.security.TransferTokenValidator
import ru.hytalemodding.lineage.proxy.session.PlayerSession
import ru.hytalemodding.lineage.proxy.util.Logging
import java.nio.charset.StandardCharsets

/**
 * Intercepts the Connect packet on stream 0 to inject proxy referral data.
 */
class ConnectPacketInterceptor(
    private val session: PlayerSession,
    private val tokenService: TokenService,
    private val transferTokenValidator: TransferTokenValidator,
    private val router: Router,
    private val playerManager: PlayerManagerImpl,
    private val eventBus: EventBus,
    private val transferService: PlayerTransferService,
    private val onBackendSelected: (String) -> Unit,
) : ChannelInboundHandlerAdapter() {
    private val logger = Logging.logger(ConnectPacketInterceptor::class.java)
    private var handled = false
    private var backendStream: QuicStreamChannel? = null
    private var pendingPacket: ByteBuf? = null
    private var pendingRemainder: ByteBuf? = null
    private var cumulation: ByteBuf? = null
    private var ctxRef: ChannelHandlerContext? = null
    private var bridgeAttached = false

    /**
     * Assigns the backend stream used for forwarding the modified Connect packet.
     */
    fun setBackendStream(stream: QuicStreamChannel) {
        backendStream = stream
        val packet = pendingPacket
        if (packet != null) {
            logger.debug("Flushing Connect packet to backend stream {}", stream.streamId())
            stream.writeAndFlush(packet)
            pendingPacket = null
        }
        attachBridgeIfReady()
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctxRef = ctx
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (handled || msg !is ByteBuf) {
            ctx.fireChannelRead(msg)
            return
        }

        try {
            var buf = cumulation
            if (buf == null) {
                buf = msg
            } else {
                buf.writeBytes(msg)
                ReferenceCountUtil.release(msg)
            }

            if (buf.readableBytes() < 8) {
                cumulation = if (buf === msg) Unpooled.buffer().writeBytes(buf) else buf
                return
            }

            val base = buf.readerIndex()
            val payloadLength = buf.getIntLE(base)
            val packetId = buf.getIntLE(base + 4)

            if (packetId != CONNECT_PACKET_ID) {
                logger.debug("Non-connect packet detected (id={}), bypassing", packetId)
                passThrough(ctx, buf)
                return
            }

            if (buf.readableBytes() < 8 + payloadLength) {
                logger.debug("Waiting for more data ({}/{})", buf.readableBytes(), 8 + payloadLength)
                cumulation = if (buf === msg) Unpooled.buffer().writeBytes(buf) else buf
                return
            }

            logger.info("Full Connect packet intercepted. Length: {}", payloadLength)
            val payload = buf.slice(base + 8, payloadLength)
            val connect = ConnectPacketCodec.decode(payload)
            session.playerId = connect.uuid.toString()
            val backendId = resolveBackend(connect)
            val isNewPlayer = playerManager.get(connect.uuid) == null
            val player = playerManager.getOrCreate(connect.uuid, connect.username)
            if (isNewPlayer) {
                eventBus.post(PlayerConnectEvent(player))
            }
            val previousBackend = player.backendId
            if (previousBackend == null || previousBackend == backendId) {
                player.backendId = backendId
                player.state = PlayerState.HANDSHAKING
            } else {
                transferService.transfer(player, backendId)
            }
            val token = tokenService.issueToken(connect.uuid.toString(), backendId, session.clientCertB64)
            
            val updated = connect.copy(
                referralData = token.toByteArray(StandardCharsets.UTF_8),
                referralSource = HostAddress("127.0.0.1", 25565)
            )

            val modified = ctx.alloc().buffer()
            val lengthIndex = modified.writerIndex()
            modified.writeIntLE(0)
            modified.writeIntLE(CONNECT_PACKET_ID)
            ConnectPacketCodec.encode(updated, modified)
            modified.setIntLE(lengthIndex, modified.writerIndex() - lengthIndex - 8)

            handled = true
            buf.skipBytes(8 + payloadLength)
            
            val remaining = if (buf.isReadable) buf.readRetainedSlice(buf.readableBytes()) else null
            if (buf !== msg) buf.release()
            cumulation = null

            val stream = backendStream
            if (stream != null) {
                logger.debug("Forwarding modified Connect packet immediately")
                stream.writeAndFlush(modified)
            } else {
                logger.debug("Buffering modified Connect packet")
                pendingPacket = modified
            }
            onBackendSelected(backendId)
            pendingRemainder = remaining
            attachBridgeIfReady()

        } catch (e: Exception) {
            logger.warn("Interception failed", e)
            passThrough(ctx, msg)
        }
    }

    private fun passThrough(ctx: ChannelHandlerContext, buf: ByteBuf) {
        handled = true
        cumulation = null
        val stream = backendStream
        if (stream != null) {
            ctx.pipeline().addLast(StreamBridge(stream))
            ctx.fireChannelRead(buf)
            ctx.pipeline().remove(this)
        } else {
            ctx.fireChannelRead(buf)
        }
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        cumulation?.release()
        cumulation = null
        pendingRemainder?.release()
        pendingRemainder = null
        pendingPacket?.release()
        pendingPacket = null
    }

    private fun attachBridgeIfReady() {
        val ctx = ctxRef ?: return
        val stream = backendStream ?: return
        if (!handled || bridgeAttached) return
        bridgeAttached = true
        val attach = Runnable {
            if (ctx.pipeline().context(this) == null) {
                return@Runnable
            }
            ctx.pipeline().addLast(StreamBridge(stream))
            val remainder = pendingRemainder
            if (remainder != null) {
                pendingRemainder = null
                ctx.fireChannelRead(remainder)
            }
            ctx.pipeline().remove(this)
        }
        if (ctx.executor().inEventLoop()) {
            attach.run()
        } else {
            ctx.executor().execute(attach)
        }
    }

    /**
     * Resolves the backend ID using transfer token referral data when present.
     */
    private fun resolveBackend(connect: ru.hytalemodding.lineage.proxy.protocol.ConnectPacket): String {
        var backendId = session.selectedBackendId ?: router.selectInitialBackend().id
        val referralData = connect.referralData
        if (referralData != null && referralData.isNotEmpty()) {
            val encoded = String(referralData, StandardCharsets.UTF_8)
                .trim { it <= ' ' || it == '\u0000' }
            val transfer = transferTokenValidator.tryValidate(encoded, connect.uuid.toString())
            if (transfer != null) {
                val backend = router.findBackend(transfer.targetServerId)
                if (backend != null) {
                    backendId = backend.id
                    logger.info("Transfer token selects backend {}", backendId)
                } else {
                    logger.warn("Transfer token requested unknown backend {}", transfer.targetServerId)
                }
            }
        }
        session.selectedBackendId = backendId
        return backendId
    }
}
