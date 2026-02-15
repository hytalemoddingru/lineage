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
import io.netty.util.AttributeKey
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.proxy.event.player.PlayerDisconnectEvent
import ru.hytalemodding.lineage.proxy.net.handler.ConnectPacketInterceptor
import ru.hytalemodding.lineage.proxy.net.handler.StreamBridge
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.player.PlayerTransferService
import ru.hytalemodding.lineage.proxy.routing.Router
import ru.hytalemodding.lineage.proxy.routing.RoutingDeniedException
import ru.hytalemodding.lineage.proxy.security.TokenService
import ru.hytalemodding.lineage.proxy.security.TransferTokenValidator
import ru.hytalemodding.lineage.proxy.security.RateLimitService
import ru.hytalemodding.lineage.proxy.session.SessionManager
import ru.hytalemodding.lineage.proxy.util.CertificateUtil
import ru.hytalemodding.lineage.api.routing.RoutingContext
import ru.hytalemodding.lineage.api.protocol.ClientType
import ru.hytalemodding.lineage.proxy.config.ReferralConfig
import ru.hytalemodding.lineage.proxy.observability.ProxyMetricsRegistry
import ru.hytalemodding.lineage.shared.protocol.ProtocolLimitsConfig
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import javax.net.ssl.SSLPeerUnverifiedException
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.LinkedHashSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

/**
 * Handles a single QUIC session, intercepting the connect handshake and bridging streams.
 */
class QuicSessionHandler(
    private val router: Router,
    private val sessionManager: SessionManager,
    private val tokenService: TokenService,
    private val transferTokenValidator: TransferTokenValidator,
    private val rateLimitService: RateLimitService,
    private val certs: CertificateUtil.CertPair,
    private val playerManager: PlayerManagerImpl,
    private val eventBus: EventBus,
    private val transferService: PlayerTransferService,
    private val certificatePolicyStore: BackendCertificatePolicyStore,
    private val backendAvailabilityTracker: BackendAvailabilityTracker,
    private val defaultBackendId: String,
    private val backendIds: List<String>,
    private val referralConfig: ReferralConfig,
    private val protocolLimits: ProtocolLimitsConfig,
    private val metrics: ProxyMetricsRegistry? = null,
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
    private var remoteKey: String = "unknown"
    private var clientAddress: InetSocketAddress? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        val clientChannel = ctx.channel() as QuicChannel
        this.clientChannel = clientChannel
        val remote = clientChannel.parent()?.remoteAddress() as? InetSocketAddress
        clientAddress = remote
        remoteKey = remote?.address?.hostAddress ?: remote?.hostString ?: clientChannel.remoteAddress().toString()
        val negotiatedAlpn = QuicSecurityPolicy.normalizeNegotiatedAlpn(clientChannel.sslEngine()?.applicationProtocol)
        if (!QuicSecurityPolicy.isAcceptedNegotiatedAlpn(negotiatedAlpn)) {
            metrics?.incrementHandshakeError("ALPN_MISMATCH")
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "handshake",
                    severity = "WARN",
                    reason = "ALPN_MISMATCH",
                    correlationId = session.id.toString(),
                    fields = mapOf("remoteKey" to remoteKey, "alpn" to (negotiatedAlpn ?: "none")),
                )
            )
            clientChannel.close()
            return
        }
        if (!rateLimitService.connectionPerIp.tryAcquire(remoteKey)) {
            metrics?.incrementHandshakeError("CONNECTION_RATE_LIMIT")
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "handshake",
                    severity = "WARN",
                    reason = "CONNECTION_RATE_LIMIT",
                    correlationId = session.id.toString(),
                    fields = mapOf("remoteKey" to remoteKey),
                )
            )
            clientChannel.close()
            return
        }
        logger.debug(
            "Accepted new connection: quicAddress={}, remoteKey={}",
            clientChannel.remoteAddress(),
            remoteKey,
        )
        session.attachClient(clientChannel)

        session.clientCertB64 = extractClientCertB64(clientChannel)
        session.proxyCertB64 = encodeCertificate(certs.cert)

        val context = RoutingContext(
            playerId = null,
            username = null,
            clientAddress = clientAddress,
            requestedBackendId = null,
            protocolCrc = 0,
            protocolBuild = 0,
            clientVersion = "unknown",
            clientType = ClientType.UNKNOWN,
            language = "en-US",
            identityTokenPresent = false,
        )
        try {
            val backend = router.selectInitialBackend(context)
            session.selectedBackendId = backend.id
        } catch (ex: RoutingDeniedException) {
            metrics?.incrementHandshakeError("INITIAL_ROUTE_DENIED")
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "handshake",
                    severity = "WARN",
                    reason = "INITIAL_ROUTE_DENIED",
                    correlationId = session.id.toString(),
                    fields = mapOf("remoteKey" to remoteKey, "details" to ex.reason),
                )
            )
            clientChannel.close()
            return
        }
    }

    /**
     * Handles a newly opened QUIC stream from the client.
     */
    fun handleIncomingStream(ch: QuicStreamChannel) {
        if (!rateLimitService.streamsPerSession.tryAcquire(session.id.toString())) {
            metrics?.incrementHandshakeError("STREAM_RATE_LIMIT")
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "handshake",
                    severity = "WARN",
                    reason = "STREAM_RATE_LIMIT",
                    correlationId = session.id.toString(),
                )
            )
            ch.close()
            return
        }
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
            logger.debug("Capturing Stream 0")
            var interceptorRef: ConnectPacketInterceptor? = null
            val interceptor = ConnectPacketInterceptor(
                session,
                tokenService,
                transferTokenValidator,
                rateLimitService,
                router,
                playerManager,
                eventBus,
                transferService,
                backendAvailabilityTracker,
                referralConfig,
                protocolLimits,
                remoteKey,
                clientAddress,
                metrics,
            ) { backendId, onResolved, onFailure ->
                val currentInterceptor = interceptorRef
                if (currentInterceptor == null) {
                    logger.warn("Connect interceptor not ready for backend selection")
                    ch.close()
                    return@ConnectPacketInterceptor
                }
                ensureBackendConnected(
                    backendId = backendId,
                    onConnected = { resolvedBackendId, backendChannel ->
                    backendChannel.createStream(ch.type(), object : ChannelInitializer<QuicStreamChannel>() {
                        override fun initChannel(backendStream: QuicStreamChannel) {
                            backendStream.pipeline().addLast(StreamBridge(ch))
                        }
                    }).addListener { future ->
                        if (future.isSuccess) {
                            val backendStream = future.get() as QuicStreamChannel
                            currentInterceptor.setBackendStream(backendStream)
                            onResolved(resolvedBackendId)
                        } else {
                            logger.warn("Failed to create backend stream", future.cause())
                            ch.close()
                            onFailure(future.cause())
                        }
                    }
                    },
                    onFailure = { error ->
                        ch.close()
                        onFailure(error)
                    },
                )
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
                    logger.info(
                        "{} disconnected [{}]",
                        removed.username,
                        removed.backendId ?: "unknown",
                    )
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
    private fun ensureBackendConnected(
        backendId: String,
        onConnected: (resolvedBackendId: String, channel: QuicChannel) -> Unit,
        onFailure: (Throwable?) -> Unit,
    ) {
        val existing = backendConnectFuture
        if (existing != null) {
            existing.whenComplete { channel, error ->
                if (error != null) {
                    logger.warn("Backend connection failed", error)
                    onFailure(error)
                } else if (channel != null) {
                    onConnected(session.selectedBackendId ?: backendId, channel)
                }
            }
            return
        }

        val future = CompletableFuture<QuicChannel>()
        backendConnectFuture = future
        connectBackendWithFallback(
            requestedBackendId = backendId,
            allowFallback = true,
        ) { backendChannel, resolvedBackendId, error ->
            if (backendChannel != null && resolvedBackendId != null) {
                session.selectedBackendId = resolvedBackendId
                logger.debug("Backend connected to {}", resolvedBackendId)
                session.attachBackend(backendChannel)
                future.complete(backendChannel)
                synchronized(pendingStreams) {
                    pendingStreams.forEach { handleIncomingStream(it) }
                    pendingStreams.clear()
                }
                onConnected(resolvedBackendId, backendChannel)
                return@connectBackendWithFallback
            }
            val cause = error ?: RuntimeException("Backend connect failed")
            future.completeExceptionally(cause)
            onFailure(cause)
        }
    }

    private fun connectBackendWithFallback(
        requestedBackendId: String,
        allowFallback: Boolean,
        callback: (channel: QuicChannel?, resolvedBackendId: String?, error: Throwable?) -> Unit,
    ) {
        val immediateFallbackId = selectFallbackBackend(excludingBackendId = requestedBackendId)
        if (allowFallback && backendAvailabilityTracker.isTemporarilyUnavailable(requestedBackendId)) {
            if (immediateFallbackId != null) {
                logger.warn(
                    "Backend {} is temporarily unavailable, routing to fallback {}",
                    requestedBackendId,
                    immediateFallbackId,
                )
                connectBackend(immediateFallbackId, callback)
                return
            }
        }

        connectBackend(requestedBackendId) { channel, connectedBackendId, error ->
            if (channel != null && connectedBackendId != null) {
                callback(channel, connectedBackendId, null)
                return@connectBackend
            }
            if (allowFallback) {
                val fallbackId = selectFallbackBackend(excludingBackendId = requestedBackendId)
                if (fallbackId != null) {
                    logger.warn(
                        "Backend {} connection failed, retrying fallback {}",
                        requestedBackendId,
                        fallbackId,
                        error,
                    )
                    connectBackend(fallbackId) { fallbackChannel, resolvedFallbackId, fallbackError ->
                        if (fallbackChannel != null && resolvedFallbackId != null) {
                            callback(fallbackChannel, resolvedFallbackId, null)
                        } else {
                            callback(null, null, fallbackError ?: error)
                        }
                    }
                    return@connectBackend
                }
            }
            callback(null, null, error)
        }
    }

    private fun selectFallbackBackend(excludingBackendId: String): String? {
        val preferred = LinkedHashSet<String>()
        if (defaultBackendId != excludingBackendId) {
            preferred.add(defaultBackendId)
        }
        for (id in backendIds) {
            if (id != excludingBackendId && id != defaultBackendId) {
                preferred.add(id)
            }
        }
        for (candidate in preferred) {
            if (router.findBackend(candidate) != null && !backendAvailabilityTracker.isTemporarilyUnavailable(candidate)) {
                return candidate
            }
        }
        return preferred.firstOrNull { router.findBackend(it) != null }
    }

    private fun connectBackend(
        backendId: String,
        callback: (channel: QuicChannel?, backendId: String?, error: Throwable?) -> Unit,
    ) {
        val backend = router.findBackend(backendId)
        if (backend == null) {
            callback(null, null, IllegalStateException("Backend not found: $backendId"))
            return
        }

        val clientChannel = clientChannel
        if (clientChannel == null) {
            callback(null, null, IllegalStateException("Client channel not ready"))
            return
        }

        val sslContext = QuicSslContextBuilder.forClient()
            .applicationProtocols(*QuicSecurityPolicy.advertisedAlpnProtocols())
            .keyManager(certs.key, null, certs.cert)
            .trustManager(certificatePolicyStore.trustManagerFactoryFor(backend))
            .build()

        val codec = QuicClientCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(
                QuicTransportProfile.IDLE_TIMEOUT_VALUE,
                QuicTransportProfile.IDLE_TIMEOUT_UNIT,
            )
            .initialMaxData(QuicTransportProfile.INITIAL_MAX_DATA)
            .initialMaxStreamDataBidirectionalLocal(QuicTransportProfile.INITIAL_MAX_STREAM_DATA_BIDI)
            .initialMaxStreamDataBidirectionalRemote(QuicTransportProfile.INITIAL_MAX_STREAM_DATA_BIDI)
            .initialMaxStreamsBidirectional(QuicTransportProfile.INITIAL_MAX_STREAMS_BIDIRECTIONAL)
            .build()

        val bootstrap = Bootstrap()
            .group(clientChannel.eventLoop())
            .channel(NioDatagramChannel::class.java)
            .handler(codec)

        bootstrap.bind(0).addListener { bindFuture ->
            if (!bindFuture.isSuccess) {
                callback(null, backend.id, bindFuture.cause() ?: RuntimeException("UDP bind failed"))
                backendAvailabilityTracker.markUnavailable(backend.id)
                return@addListener
            }

            val localUdpChannel = (bindFuture as ChannelFuture).channel()
            val remote = InetSocketAddress(backend.host, backend.port)
            val connectBootstrap = QuicChannel.newBootstrap(localUdpChannel)
                .remoteAddress(remote)
                .streamHandler(object : ChannelInitializer<QuicStreamChannel>() {
                    override fun initChannel(ch: QuicStreamChannel) {
                        ch.pipeline().addLast(StreamBridge(clientChannel))
                    }
                })
            val completed = AtomicBoolean(false)

            val timeoutTask = clientChannel.eventLoop().schedule({
                if (completed.compareAndSet(false, true)) {
                    backendAvailabilityTracker.markUnavailable(backend.id)
                    callback(
                        null,
                        backend.id,
                        TimeoutException("Backend connect timeout after ${QuicTransportProfile.BACKEND_CONNECT_TIMEOUT_MILLIS}ms"),
                    )
                    localUdpChannel.close()
                }
            }, QuicTransportProfile.BACKEND_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

            connectBootstrap.connect().addListener(io.netty.util.concurrent.FutureListener<QuicChannel> { connectFuture ->
                timeoutTask.cancel(false)
                if (!completed.compareAndSet(false, true)) {
                    return@FutureListener
                }
                if (!connectFuture.isSuccess) {
                    localUdpChannel.close()
                    backendAvailabilityTracker.markUnavailable(backend.id)
                    callback(null, backend.id, connectFuture.cause() ?: RuntimeException("Backend connect failed"))
                    return@FutureListener
                }
                val backendChannel = connectFuture.getNow()
                val negotiatedAlpn = QuicSecurityPolicy.normalizeNegotiatedAlpn(backendChannel.sslEngine()?.applicationProtocol)
                if (!QuicSecurityPolicy.isAcceptedNegotiatedAlpn(negotiatedAlpn)) {
                    backendChannel.close()
                    backendAvailabilityTracker.markUnavailable(backend.id)
                    callback(
                        null,
                        backend.id,
                        IllegalStateException("Backend ALPN mismatch: ${negotiatedAlpn ?: "none"}"),
                    )
                    return@FutureListener
                }
                if (!certificatePolicyStore.verifyAndRecord(backend, backendChannel)) {
                    backendChannel.close()
                    backendAvailabilityTracker.markUnavailable(backend.id)
                    callback(
                        null,
                        backend.id,
                        IllegalStateException("Backend certificate policy rejected peer certificate"),
                    )
                    return@FutureListener
                }
                backendAvailabilityTracker.markAvailable(backend.id)
                callback(backendChannel, backend.id, null)
            })
        }
    }

    private fun extractClientCertB64(channel: QuicChannel): String? {
        return try {
            val sslEngine = channel.sslEngine() ?: return null
            val peerCerts = sslEngine.session.peerCertificates
            val cert = peerCerts.firstOrNull { it is X509Certificate } as? X509Certificate ?: return null
            encodeCertificate(cert)
        } catch (_: SSLPeerUnverifiedException) {
            null
        } catch (e: Exception) {
            logger.warn("Failed to capture client certificate", e)
            null
        }
    }

    private fun encodeCertificate(cert: X509Certificate): String? {
        return runCatching {
            Base64.getUrlEncoder().withoutPadding().encodeToString(cert.encoded)
        }.getOrNull()
    }

}
