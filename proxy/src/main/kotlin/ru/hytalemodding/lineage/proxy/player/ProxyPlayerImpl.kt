/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicConnectionPathStats
import ru.hytalemodding.lineage.api.player.PlayerState
import ru.hytalemodding.lineage.api.player.ProxyPlayer
import ru.hytalemodding.lineage.proxy.util.Logging
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Mutable proxy player implementation.
 */
class ProxyPlayerImpl(
    override val id: UUID,
    override var username: String,
    private val transferServiceProvider: () -> PlayerTransferService?,
    private val messageSenderProvider: () -> ((ProxyPlayerImpl, String) -> Boolean),
) : ProxyPlayer {
    private val logger = Logging.logger(ProxyPlayerImpl::class.java)
    override var state: PlayerState = PlayerState.CONNECTING
    override var backendId: String? = null
    var language: String = "en-US"
    @Volatile
    var clientIp: String? = null
        private set
    @Volatile
    var clientEndpoint: String? = null
        private set
    @Volatile
    var sessionId: UUID? = null
        private set
    @Volatile
    var protocolCrc: Int? = null
        private set
    @Volatile
    var protocolBuildNumber: Int? = null
        private set
    @Volatile
    var clientVersion: String? = null
        private set
    @Volatile
    var clientType: Byte? = null
        private set
    @Volatile
    var connectedAtMillis: Long = System.currentTimeMillis()
        private set
    @Volatile
    var lastPingMillis: Long? = null
        private set
    @Volatile
    private var clientQuicChannel: QuicChannel? = null

    override fun transferTo(backendId: String) {
        val service = transferServiceProvider()
        if (service == null || !service.requestTransfer(this, backendId)) {
            applyTransfer(backendId)
        }
    }

    override fun disconnect(reason: String?) {
        this.state = PlayerState.DISCONNECTED
    }

    override fun sendMessage(message: String) {
        if (!messageSenderProvider()(this, message)) {
            logger.warn("Unable to deliver proxy message to {}", id)
        }
    }

    fun bindConnectionMetadata(
        sessionId: UUID,
        clientChannel: QuicChannel?,
        remoteAddress: InetSocketAddress?,
        protocolCrc: Int,
        protocolBuildNumber: Int,
        clientVersion: String,
        clientType: Byte,
        language: String,
    ) {
        this.sessionId = sessionId
        this.clientQuicChannel = clientChannel
        this.clientIp = remoteAddress?.address?.hostAddress ?: remoteAddress?.hostString
        this.clientEndpoint = remoteAddress?.let { "${it.hostString}:${it.port}" }
        this.protocolCrc = protocolCrc
        this.protocolBuildNumber = protocolBuildNumber
        this.clientVersion = clientVersion
        this.clientType = clientType
        this.language = language
        this.connectedAtMillis = System.currentTimeMillis()
    }

    fun requestPingMillis(onResult: (Long?) -> Unit) {
        val channel = clientQuicChannel
        if (channel == null || !channel.isActive) {
            onResult(lastPingMillis)
            return
        }
        channel.collectPathStats(0).addListener { future ->
            if (!future.isSuccess) {
                onResult(lastPingMillis)
                return@addListener
            }
            val stats = future.getNow() as? QuicConnectionPathStats
            val rttNanos = stats?.rtt() ?: 0L
            if (rttNanos <= 0L) {
                onResult(lastPingMillis)
                return@addListener
            }
            val ping = TimeUnit.NANOSECONDS.toMillis(rttNanos).coerceAtLeast(1L)
            lastPingMillis = ping
            onResult(ping)
        }
    }

    internal fun applyTransfer(targetBackendId: String) {
        backendId = targetBackendId
        state = PlayerState.TRANSFERRING
    }
}
