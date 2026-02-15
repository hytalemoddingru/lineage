/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.messaging

import ru.hytalemodding.lineage.shared.messaging.HandshakePacket
import ru.hytalemodding.lineage.shared.messaging.MessagePacket
import ru.hytalemodding.lineage.shared.messaging.MessagingProtocol
import ru.hytalemodding.lineage.proxy.util.Logging
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDP server that accepts authenticated messaging packets.
 */
class MessagingServer(
    private val bindAddress: InetSocketAddress,
    private val secret: ByteArray,
    private val onMessage: (SocketAddress, String, ByteArray) -> Unit,
) : AutoCloseable {
    private val logger = Logging.logger(MessagingServer::class.java)
    private val socket = DatagramSocket(bindAddress)
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val authorized = ConcurrentHashMap<SocketAddress, Long>()

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        executor.execute { loop() }
        logger.info("Messaging UDP server listening on {}", bindAddress)
    }

    fun isRunning(): Boolean = running.get()

    fun send(address: SocketAddress, channelId: String, payload: ByteArray) {
        val data = MessagingProtocol.encodeMessage(secret, channelId, payload)
        val packet = DatagramPacket(data, data.size, address)
        socket.send(packet)
    }

    fun broadcast(channelId: String, payload: ByteArray) {
        val now = System.currentTimeMillis()
        val targets = authorized.filterValues { it > now }.keys
        for (address in targets) {
            send(address, channelId, payload)
        }
    }

    override fun close() {
        running.set(false)
        socket.close()
        executor.shutdown()
    }

    private fun loop() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (running.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val data = packet.data.copyOfRange(0, packet.length)
                handlePacket(packet.socketAddress, data)
            } catch (ex: Exception) {
                if (running.get()) {
                    logger.warn("Messaging UDP receive failed", ex)
                }
            }
        }
    }

    private fun handlePacket(address: SocketAddress, data: ByteArray) {
        val packet = MessagingProtocol.decode(data) ?: return
        when (packet) {
            is HandshakePacket -> handleHandshake(address, packet)
            is MessagePacket -> handleMessage(address, packet)
            else -> Unit
        }
    }

    private fun handleHandshake(address: SocketAddress, packet: HandshakePacket) {
        if (!MessagingProtocol.verifyHandshake(packet, secret)) {
            return
        }
        if (!isTimestampValid(packet.timestampMillis)) {
            return
        }
        authorized[address] = System.currentTimeMillis() + AUTH_TTL_MILLIS
        val ack = MessagingProtocol.encodeHandshakeAck(secret, packet.nonce)
        socket.send(DatagramPacket(ack, ack.size, address))
    }

    private fun handleMessage(address: SocketAddress, packet: MessagePacket) {
        if (!MessagingProtocol.verifyMessage(packet, secret)) {
            return
        }
        val expiresAt = authorized[address] ?: return
        if (expiresAt < System.currentTimeMillis()) {
            authorized.remove(address)
            return
        }
        authorized[address] = System.currentTimeMillis() + AUTH_TTL_MILLIS
        onMessage(address, packet.channelId, packet.payload)
    }

    private fun isTimestampValid(timestampMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        return timestampMillis in (now - MAX_CLOCK_SKEW_MILLIS)..(now + MAX_CLOCK_SKEW_MILLIS)
    }

    private companion object {
        private const val MAX_PACKET_SIZE = 65_507
        private const val AUTH_TTL_MILLIS = 120_000L
        private const val MAX_CLOCK_SKEW_MILLIS = 120_000L
    }
}
