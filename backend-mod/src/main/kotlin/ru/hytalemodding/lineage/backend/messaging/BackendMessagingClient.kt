/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.messaging

import ru.hytalemodding.lineage.shared.messaging.HandshakeAckPacket
import ru.hytalemodding.lineage.shared.messaging.MessagePacket
import ru.hytalemodding.lineage.shared.messaging.MessagingProtocol
import ru.hytalemodding.lineage.shared.messaging.MessagingPacket
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDP messaging client for backend-mod.
 */
class BackendMessagingClient(
    private val serverAddress: InetSocketAddress,
    private val secret: ByteArray,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(BackendMessagingClient::class.java)
    private val socket = DatagramSocket()
    private val running = AtomicBoolean(false)
    private val receiver = Executors.newSingleThreadExecutor()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val handlers = ConcurrentHashMap<String, (ByteArray) -> Unit>()
    private val random = SecureRandom()

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        sendHandshake()
        scheduler.scheduleAtFixedRate({ sendHandshake() }, 30, 30, TimeUnit.SECONDS)
        receiver.execute { loop() }
        logger.info("Backend messaging client started for {}", serverAddress)
    }

    fun registerChannel(id: String, handler: (ByteArray) -> Unit) {
        validateChannelId(id)
        handlers[id] = handler
    }

    fun unregisterChannel(id: String) {
        handlers.remove(id)
    }

    fun send(channelId: String, payload: ByteArray) {
        validateChannelId(channelId)
        val data = MessagingProtocol.encodeMessage(secret, channelId, payload)
        socket.send(DatagramPacket(data, data.size, serverAddress))
    }

    override fun close() {
        running.set(false)
        socket.close()
        receiver.shutdown()
        scheduler.shutdown()
    }

    private fun loop() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (running.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                if (packet.socketAddress != serverAddress) {
                    continue
                }
                val data = packet.data.copyOfRange(0, packet.length)
                val decoded = MessagingProtocol.decode(data) ?: continue
                handlePacket(decoded)
            } catch (ex: Exception) {
                if (running.get()) {
                    logger.warn("Backend messaging receive failed", ex)
                }
            }
        }
    }

    private fun handlePacket(packet: MessagingPacket) {
        when (packet) {
            is MessagePacket -> handleMessage(packet)
            is HandshakeAckPacket -> {
                if (!MessagingProtocol.verifyHandshakeAck(packet, secret)) {
                    logger.debug("Invalid handshake ack")
                }
            }
            else -> Unit
        }
    }

    private fun handleMessage(packet: MessagePacket) {
        if (!MessagingProtocol.verifyMessage(packet, secret)) {
            return
        }
        handlers[packet.channelId]?.invoke(packet.payload)
    }

    private fun sendHandshake() {
        val nonce = ByteArray(NONCE_SIZE)
        random.nextBytes(nonce)
        val data = MessagingProtocol.encodeHandshake(secret, System.currentTimeMillis(), nonce)
        socket.send(DatagramPacket(data, data.size, serverAddress))
    }

    private fun validateChannelId(id: String) {
        val normalized = id.trim()
        require(normalized.isNotEmpty()) { "Channel id must not be blank" }
        require(normalized.length <= 64) { "Channel id must be <= 64 characters" }
        require(CHANNEL_ID_REGEX.matches(normalized)) { "Channel id has invalid characters: $id" }
    }

    private companion object {
        private const val MAX_PACKET_SIZE = 65_507
        private const val NONCE_SIZE = 16
        private val CHANNEL_ID_REGEX = Regex("^[A-Za-z0-9_.:-]+$")
    }
}
