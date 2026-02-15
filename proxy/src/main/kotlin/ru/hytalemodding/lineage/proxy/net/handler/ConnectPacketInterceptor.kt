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
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.util.ReferenceCountUtil
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.player.PlayerState
import ru.hytalemodding.lineage.proxy.event.player.PlayerConnectEvent
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.player.PlayerTransferService
import ru.hytalemodding.lineage.api.routing.RoutingContext
import ru.hytalemodding.lineage.api.protocol.ClientType
import ru.hytalemodding.lineage.proxy.protocol.CONNECT_PACKET_ID
import ru.hytalemodding.lineage.proxy.protocol.ConnectPacketCodec
import ru.hytalemodding.lineage.proxy.protocol.HostAddress
import ru.hytalemodding.lineage.proxy.routing.Router
import ru.hytalemodding.lineage.proxy.routing.RoutingDeniedException
import ru.hytalemodding.lineage.proxy.security.RateLimitService
import ru.hytalemodding.lineage.proxy.security.TokenService
import ru.hytalemodding.lineage.proxy.security.TransferTokenValidator
import ru.hytalemodding.lineage.proxy.session.PlayerSession
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityTracker
import ru.hytalemodding.lineage.proxy.util.Logging
import ru.hytalemodding.lineage.proxy.config.ReferralConfig
import ru.hytalemodding.lineage.proxy.observability.ProxyMetricsRegistry
import ru.hytalemodding.lineage.shared.protocol.ProtocolLimitsConfig
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * Intercepts the Connect packet on stream 0 to inject proxy referral data.
 */
class ConnectPacketInterceptor(
    private val session: PlayerSession,
    private val tokenService: TokenService,
    private val transferTokenValidator: TransferTokenValidator,
    private val rateLimitService: RateLimitService,
    private val router: Router,
    private val playerManager: PlayerManagerImpl,
    private val eventBus: EventBus,
    private val transferService: PlayerTransferService,
    private val backendAvailabilityTracker: BackendAvailabilityTracker,
    private val referralConfig: ReferralConfig,
    private val protocolLimits: ProtocolLimitsConfig,
    private val remoteKey: String,
    private val clientAddress: InetSocketAddress?,
    private val metrics: ProxyMetricsRegistry? = null,
    private val onBackendSelected: (
        backendId: String,
        onResolved: (resolvedBackendId: String) -> Unit,
        onFailure: (Throwable?) -> Unit,
    ) -> Unit,
) : ChannelInboundHandlerAdapter() {
    private val logger = Logging.logger(ConnectPacketInterceptor::class.java)
    private var handled = false
    private var backendStream: QuicStreamChannel? = null
    private var pendingPacket: ByteBuf? = null
    private var pendingRemainder: ByteBuf? = null
    private var cumulation: ByteBuf? = null
    private var ctxRef: ChannelHandlerContext? = null
    private var bridgeAttached = false
    private var handshakeLease: ru.hytalemodding.lineage.proxy.security.InFlightLimiter.Lease? = null

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
        if (handshakeLease == null) {
            val lease = rateLimitService.handshakeInFlight.tryAcquire()
            if (lease == null) {
                logger.warn(
                    "{}",
                    StructuredLog.event(
                        category = "handshake",
                        severity = "WARN",
                        reason = "HANDSHAKE_INFLIGHT_LIMIT",
                        correlationId = session.id.toString(),
                        fields = mapOf("remoteKey" to remoteKey),
                    )
                )
                recordInvalidAndClose(ctx, msg, "HANDSHAKE_INFLIGHT_LIMIT")
                return
            }
            handshakeLease = lease
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
            if (payloadLength <= 0 || payloadLength > protocolLimits.maxConnectSize) {
                recordInvalidAndClose(ctx, buf, "INVALID_CONNECT_LENGTH")
                return
            }
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

            if (!rateLimitService.handshakePerIp.tryAcquire(remoteKey)) {
                recordInvalidAndClose(ctx, buf, "HANDSHAKE_RATE_LIMIT")
                return
            }
            logger.debug("Full Connect packet intercepted. Length: {}", payloadLength)
            val payload = buf.slice(base + 8, payloadLength)
            val connect = try {
                ConnectPacketCodec.decode(payload, protocolLimits)
            } catch (ex: IllegalArgumentException) {
                recordInvalidAndClose(ctx, buf, "CONNECT_PAYLOAD_REJECTED")
                return
            }
            logger.debug(
                "Client {} ({}) protocol crc={}, build={}, version={}",
                connect.username,
                connect.uuid,
                connect.protocolCrc,
                connect.protocolBuildNumber,
                connect.clientVersion,
            )
            session.playerId = connect.uuid.toString()
            val backendId = try {
                resolveBackend(connect)
            } catch (ex: RoutingDeniedException) {
                rejectAndClose(ctx, buf, "ROUTE_DENIED")
                return
            }
            val isNewPlayer = playerManager.get(connect.uuid) == null
            val player = playerManager.getOrCreate(connect.uuid, connect.username, connect.language)
            player.bindConnectionMetadata(
                sessionId = session.id,
                clientChannel = session.clientChannel as? QuicChannel,
                remoteAddress = clientAddress,
                protocolCrc = connect.protocolCrc,
                protocolBuildNumber = connect.protocolBuildNumber,
                clientVersion = connect.clientVersion,
                clientType = connect.clientType,
                language = connect.language,
            )
            if (isNewPlayer) {
                logger.info("{} connected [{}]", connect.username, backendId)
                eventBus.post(PlayerConnectEvent(player))
            }
            val previousBackend = player.backendId
            if (previousBackend == null || previousBackend == backendId) {
                player.backendId = backendId
                player.state = PlayerState.HANDSHAKING
            } else {
                transferService.transfer(player, backendId)
            }
            handled = true
            releaseHandshakeLease()
            buf.skipBytes(8 + payloadLength)
            val remaining = if (buf.isReadable) buf.readRetainedSlice(buf.readableBytes()) else null
            if (buf !== msg) buf.release()
            cumulation = null
            pendingRemainder = remaining

            onBackendSelected(
                backendId,
                { resolvedBackendId ->
                    val proxyCertB64 = session.proxyCertB64
                    if (proxyCertB64.isNullOrBlank()) {
                        rejectAndClose(ctx, ctx.alloc().buffer(0), "MISSING_PROXY_CERT")
                        return@onBackendSelected
                    }
                    val clientCertB64 = session.clientCertB64?.takeIf { it.isNotBlank() } ?: proxyCertB64.also {
                        logger.warn(
                            "{}",
                            StructuredLog.event(
                                category = "handshake",
                                severity = "WARN",
                                reason = "MISSING_CLIENT_CERT_FALLBACK_PROXY_CERT",
                                correlationId = session.id.toString(),
                                fields = mapOf("remoteKey" to remoteKey),
                            )
                        )
                    }
                    val token = tokenService.issueToken(connect.uuid.toString(), resolvedBackendId, clientCertB64, proxyCertB64)
                    val tokenBytes = token.toByteArray(StandardCharsets.UTF_8)
                    if (tokenBytes.size > protocolLimits.maxReferralDataLength) {
                        rejectAndClose(ctx, ctx.alloc().buffer(0), "REFERRAL_TOKEN_TOO_LARGE")
                        return@onBackendSelected
                    }
                    val updated = connect.copy(
                        referralData = tokenBytes,
                        referralSource = HostAddress(referralConfig.host, referralConfig.port)
                    )
                    val modified = ctx.alloc().buffer()
                    val lengthIndex = modified.writerIndex()
                    modified.writeIntLE(0)
                    modified.writeIntLE(CONNECT_PACKET_ID)
                    ConnectPacketCodec.encode(updated, modified)
                    modified.setIntLE(lengthIndex, modified.writerIndex() - lengthIndex - 8)

                    session.selectedBackendId = resolvedBackendId
                    val stream = backendStream
                    if (stream != null) {
                        logger.debug("Forwarding modified Connect packet immediately")
                        stream.writeAndFlush(modified)
                    } else {
                        logger.debug("Buffering modified Connect packet")
                        pendingPacket = modified
                    }
                    attachBridgeIfReady()
                },
                {
                    rejectAndClose(ctx, ctx.alloc().buffer(0), "BACKEND_UNAVAILABLE")
                },
            )

        } catch (e: Exception) {
            recordInvalidAndClose(ctx, msg, "INTERCEPTOR_EXCEPTION")
        }
    }

    private fun passThrough(ctx: ChannelHandlerContext, buf: ByteBuf) {
        handled = true
        releaseHandshakeLease()
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
        releaseHandshakeLease()
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
            ctx.pipeline().addLast(ClientLanguageUpdateInterceptor(session, playerManager))
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

    private fun recordInvalidAndClose(ctx: ChannelHandlerContext, msg: Any, reason: String) {
        releaseHandshakeLease()
        metrics?.incrementHandshakeError(reason)
        logger.warn(
            "{}",
            StructuredLog.event(
                category = "handshake",
                severity = "WARN",
                reason = reason,
                correlationId = session.id.toString(),
                fields = mapOf("remoteKey" to remoteKey),
            )
        )
        try {
            rateLimitService.invalidPacketsPerSession.tryAcquire(session.id.toString())
        } finally {
            if (ReferenceCountUtil.refCnt(msg) > 0) {
                ReferenceCountUtil.release(msg)
            }
            ctx.close()
        }
    }

    private fun rejectAndClose(ctx: ChannelHandlerContext, msg: Any, reason: String) {
        releaseHandshakeLease()
        metrics?.incrementHandshakeError(reason)
        logger.warn(
            "{}",
            StructuredLog.event(
                category = "handshake",
                severity = "WARN",
                reason = reason,
                correlationId = session.id.toString(),
                fields = mapOf("remoteKey" to remoteKey),
            )
        )
        try {
            if (ReferenceCountUtil.refCnt(msg) > 0) {
                ReferenceCountUtil.release(msg)
            }
        } finally {
            ctx.close()
        }
    }

    private fun releaseHandshakeLease() {
        handshakeLease?.close()
        handshakeLease = null
    }

    /**
     * Resolves the backend ID using transfer token referral data when present.
     */
    private fun resolveBackend(connect: ru.hytalemodding.lineage.proxy.protocol.ConnectPacket): String {
        var requestedBackendId: String? = session.selectedBackendId
        val referralData = connect.referralData
        if (referralData != null && referralData.isNotEmpty()) {
            val encoded = String(referralData, StandardCharsets.UTF_8)
                .trim { it <= ' ' || it == '\u0000' }
            val transfer = transferTokenValidator.tryValidate(encoded, connect.uuid.toString())
            if (transfer != null) {
                val backend = router.findBackend(transfer.targetServerId)
                if (backend != null) {
                    requestedBackendId = backend.id
                    logger.debug("Transfer token selects backend {}", requestedBackendId)
                } else {
                    logger.warn("Transfer token requested unknown backend {}", transfer.targetServerId)
                }
            } else if (transferTokenValidator.isTransferTokenCandidate(encoded)) {
                throw RoutingDeniedException("Invalid transfer token")
            }
        }
        val context = RoutingContext(
            playerId = connect.uuid,
            username = connect.username,
            clientAddress = clientAddress,
            requestedBackendId = requestedBackendId,
            protocolCrc = connect.protocolCrc,
            protocolBuild = connect.protocolBuildNumber,
            clientVersion = connect.clientVersion,
            clientType = ClientType.fromId(connect.clientType),
            language = connect.language,
            identityTokenPresent = !connect.identityToken.isNullOrBlank(),
        )
        val backend = router.selectBackend(context)
        if (backendAvailabilityTracker.isTemporarilyUnavailable(backend.id)) {
            val fallback = router.selectInitialBackend(context.copy(requestedBackendId = null))
            if (fallback.id != backend.id && !backendAvailabilityTracker.isTemporarilyUnavailable(fallback.id)) {
                logger.warn(
                    "{}",
                    StructuredLog.event(
                        category = "routing",
                        severity = "WARN",
                        reason = "BACKEND_TEMPORARILY_UNAVAILABLE",
                        correlationId = session.id.toString(),
                        fields = mapOf("requested" to backend.id, "fallback" to fallback.id),
                    )
                )
                session.selectedBackendId = fallback.id
                return fallback.id
            }
        }
        session.selectedBackendId = backend.id
        return backend.id
    }
}
