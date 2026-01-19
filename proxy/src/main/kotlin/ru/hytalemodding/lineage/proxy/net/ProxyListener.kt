/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicServerCodecBuilder
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import ru.hytalemodding.lineage.proxy.config.ProxyConfig
import ru.hytalemodding.lineage.proxy.routing.Router
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.security.TokenService
import ru.hytalemodding.lineage.proxy.security.TransferTokenValidator
import ru.hytalemodding.lineage.proxy.security.RateLimitService
import ru.hytalemodding.lineage.proxy.session.SessionManager
import ru.hytalemodding.lineage.proxy.util.CertificateUtil
import ru.hytalemodding.lineage.proxy.util.Logging
import java.util.concurrent.TimeUnit

/**
 * QUIC listener that accepts client sessions and wires stream handling.
 */
class ProxyListener(
    private val config: ProxyConfig,
    private val router: Router,
    private val sessionManager: SessionManager,
    private val tokenService: TokenService,
    private val transferTokenValidator: TransferTokenValidator,
    private val rateLimitService: RateLimitService,
    private val playerManager: PlayerManagerImpl,
    private val eventBus: EventBus,
) : AutoCloseable {
    private val logger = Logging.logger(ProxyListener::class.java)
    private val group = NioEventLoopGroup()
    private var channel: Channel? = null

    /**
     * Starts the listener and returns the bound channel.
     */
    fun start(): Channel {
        val certs = CertificateUtil.generateSelfSigned()
        val sslContext = QuicSslContextBuilder.forServer(certs.key, null, certs.cert)
            .applicationProtocols("hytale/1")
            .clientAuth(ClientAuth.REQUIRE)
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()

        val codec = QuicServerCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(30, TimeUnit.SECONDS)
            .initialMaxData(524288L)
            .initialMaxStreamDataBidirectionalLocal(131072)
            .initialMaxStreamDataBidirectionalRemote(131072)
            .initialMaxStreamsBidirectional(1)
            .handler(object : ChannelInitializer<QuicChannel>() {
                override fun initChannel(ch: QuicChannel) {
                    val sessionHandler = QuicSessionHandler(
                        router,
                        sessionManager,
                        tokenService,
                        transferTokenValidator,
                        rateLimitService,
                        certs,
                        playerManager,
                        eventBus,
                        config.referral,
                        config.limits,
                    )
                    ch.attr(QuicSessionHandler.SESSION_HANDLER_KEY).set(sessionHandler)
                    ch.pipeline().addLast(sessionHandler)
                }
            })
            .streamHandler(object : ChannelInitializer<QuicStreamChannel>() {
                override fun initChannel(ch: QuicStreamChannel) {
                    val parent = ch.parent()
                    val sessionHandler = parent.attr(QuicSessionHandler.SESSION_HANDLER_KEY).get()
                    if (sessionHandler != null) {
                        sessionHandler.handleIncomingStream(ch)
                    } else {
                        ch.close()
                    }
                }
            })
            .build()

        val bootstrap = Bootstrap()
            .group(group)
            .channel(NioDatagramChannel::class.java)
            .handler(codec)

        val future = bootstrap.bind(config.listener.host, config.listener.port).sync()
        if (future.isSuccess) {
            logger.info("QUIC Proxy listener bound to {}:{}", config.listener.host, config.listener.port)
        }
        channel = future.channel()
        return channel!!
    }

    override fun close() {
        group.shutdownGracefully()
    }
}
