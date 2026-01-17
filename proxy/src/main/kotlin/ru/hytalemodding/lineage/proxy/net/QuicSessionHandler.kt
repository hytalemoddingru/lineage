/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.incubator.codec.quic.*
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.AttributeKey
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.proxy.event.player.PlayerDisconnectEvent
import ru.hytalemodding.lineage.proxy.net.handler.ConnectPacketInterceptor
import ru.hytalemodding.lineage.proxy.net.handler.StreamBridge
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.player.PlayerTransferService
import ru.hytalemodding.lineage.proxy.routing.Router
import ru.hytalemodding.lineage.proxy.security.TokenService
import ru.hytalemodding.lineage.proxy.security.TransferTokenValidator
import ru.hytalemodding.lineage.proxy.session.SessionManager
import ru.hytalemodding.lineage.proxy.util.CertificateUtil
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.UUID

/**
 * Handles a single QUIC session, intercepting the connect handshake and bridging streams.
 */
class QuicSessionHandler(
    private val router: Router,
    private val sessionManager: SessionManager,
    private val tokenService: TokenService,
    private val transferTokenValidator: TransferTokenValidator,
    private val certs: CertificateUtil.CertPair,
    private val playerManager: PlayerManagerImpl,
    private val eventBus: EventBus,
) : ChannelInboundHandlerAdapter() {
    companion object {
        val SESSION_HANDLER_KEY: AttributeKey<QuicSessionHandler> =
            AttributeKey.valueOf("lineage.sessionHandler")
    }

    private val session = sessionManager.create()
    private val pendingStreams = mutableListOf<QuicStreamChannel>()
    private val logger = ru.hytalemodding.lineage.proxy.util.Logging.logger(QuicSessionHandler::class.java)
    private var backendConnectFuture: CompletableFuture<QuicChannel>? = null
    private var clientChannel: QuicChannel? = null
    private val transferService = PlayerTransferService(eventBus)

    override fun channelActive(ctx: ChannelHandlerContext) {
        val clientChannel = ctx.channel() as QuicChannel
        this.clientChannel = clientChannel
        logger.info("New connection from {}", clientChannel.remoteAddress())
        session.attachClient(clientChannel)

        try {
            val hash = MessageDigest.getInstance("SHA-256").digest(certs.cert.encoded)
            session.clientCertB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        } catch (e: Exception) {}

        val backend = router.selectInitialBackend()
        session.selectedBackendId = backend.id
    }

    /**
     * Handles a newly opened QUIC stream from the client.
     */
    fun handleIncomingStream(ch: QuicStreamChannel) {
        val backendChannel = session.backendChannel as? QuicChannel
        if (backendChannel == null && ch.streamId() != 0L) {
            ch.config().isAutoRead = false
            synchronized(pendingStreams) { pendingStreams.add(ch) }
            return
        }
        if (!ch.config().isAutoRead) {
            ch.config().isAutoRead = true
            ch.read()
        }

        if (ch.streamId() == 0L) {
            logger.info("Capturing Stream 0")
            var interceptorRef: ConnectPacketInterceptor? = null
            val interceptor = ConnectPacketInterceptor(
                session,
                tokenService,
                transferTokenValidator,
                router,
                playerManager,
                eventBus,
                transferService,
            ) { backendId ->
                val currentInterceptor = interceptorRef
                if (currentInterceptor == null) {
                    logger.warn("Connect interceptor not ready for backend selection")
                    ch.close()
                    return@ConnectPacketInterceptor
                }
                ensureBackendConnected(backendId) { backendChannel ->
                    backendChannel.createStream(ch.type(), object : ChannelInitializer<QuicStreamChannel>() {
                        override fun initChannel(backendStream: QuicStreamChannel) {
                            backendStream.pipeline().addLast(StreamBridge(ch))
                        }
                    }).addListener { future ->
                        if (future.isSuccess) {
                            val backendStream = future.get() as QuicStreamChannel
                            currentInterceptor.setBackendStream(backendStream)
                        } else {
                            logger.warn("Failed to create backend stream", future.cause())
                            ch.close()
                        }
                    }
                }
            }
            interceptorRef = interceptor
            ch.pipeline().addLast(interceptor)
        } else {
            val activeBackendChannel = backendChannel ?: run {
                ch.close()
                return
            }
            activeBackendChannel.createStream(ch.type(), object : ChannelInitializer<QuicStreamChannel>() {
                override fun initChannel(backendStream: QuicStreamChannel) {
                    backendStream.pipeline().addLast(StreamBridge(ch))
                }
            }).addListener { future ->
                if (future.isSuccess) {
                    val backendStream = future.get() as QuicStreamChannel
                    ch.pipeline().addLast(StreamBridge(backendStream))
                } else {
                    logger.warn("Failed to create backend stream", future.cause())
                    ch.close()
                }
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val playerId = session.playerId
        if (playerId != null) {
            val uuid = runCatching { UUID.fromString(playerId) }.getOrNull()
            if (uuid != null) {
                val removed = playerManager.remove(uuid)
                if (removed != null) {
                    eventBus.post(PlayerDisconnectEvent(removed))
                }
            }
        }
        sessionManager.remove(session)
        super.channelInactive(ctx)
    }

    /**
     * Ensures the backend connection is established and invokes [onConnected].
     */
    private fun ensureBackendConnected(backendId: String, onConnected: (QuicChannel) -> Unit) {
        val existing = backendConnectFuture
        if (existing != null) {
            existing.whenComplete { channel, error ->
                if (error != null) {
                    logger.warn("Backend connection failed", error)
                    clientChannel?.close()
                } else if (channel != null) {
                    onConnected(channel)
                }
            }
            return
        }

        val backend = router.findBackend(backendId)
        if (backend == null) {
            logger.warn("Backend {} not found for transfer", backendId)
            clientChannel?.close()
            return
        }

        val future = CompletableFuture<QuicChannel>()
        backendConnectFuture = future

        val sslContext = QuicSslContextBuilder.forClient()
            .applicationProtocols("hytale/1")
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .keyManager(certs.key, null, certs.cert)
            .build()

        val codec = QuicClientCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(30, TimeUnit.SECONDS)
            .initialMaxData(524288L)
            .initialMaxStreamDataBidirectionalLocal(131072)
            .initialMaxStreamDataBidirectionalRemote(131072)
            .initialMaxStreamsBidirectional(1)
            .build()

        val clientChannel = clientChannel
        if (clientChannel == null) {
            future.completeExceptionally(IllegalStateException("Client channel not ready"))
            return
        }

        val bootstrap = Bootstrap()
            .group(clientChannel.eventLoop())
            .channel(NioDatagramChannel::class.java)
            .handler(codec)

        bootstrap.bind(0).addListener { bindFuture ->
            if (bindFuture.isSuccess) {
                val localUdpChannel = (bindFuture as ChannelFuture).channel()
                val remote = InetSocketAddress(backend.host, backend.port)

                QuicChannel.newBootstrap(localUdpChannel)
                    .remoteAddress(remote)
                    .streamHandler(object : ChannelInitializer<QuicStreamChannel>() {
                        override fun initChannel(ch: QuicStreamChannel) {
                            ch.pipeline().addLast(StreamBridge(clientChannel))
                        }
                    })
                    .connect()
                    .addListener(io.netty.util.concurrent.FutureListener<QuicChannel> { connectFuture ->
                        if (connectFuture.isSuccess) {
                            val backendChannel = connectFuture.getNow()
                            logger.info("Backend connected to {}", backend.id)
                            session.attachBackend(backendChannel)
                            future.complete(backendChannel)
                            synchronized(pendingStreams) {
                                pendingStreams.forEach { handleIncomingStream(it) }
                                pendingStreams.clear()
                            }
                            onConnected(backendChannel)
                        } else {
                            logger.warn("Backend connection failed", connectFuture.cause())
                            future.completeExceptionally(connectFuture.cause() ?: RuntimeException("Backend connect failed"))
                            clientChannel.close()
                        }
                    })
            } else {
                future.completeExceptionally(bindFuture.cause() ?: RuntimeException("UDP bind failed"))
                clientChannel.close()
            }
        }
    }
}
