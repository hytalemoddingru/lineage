/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.messaging

import ru.hytalemodding.lineage.shared.crypto.Hmac
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Encoding and decoding for Lineage messaging packets.
 */
object MessagingProtocol {
    const val VERSION: Byte = 1
    const val TYPE_HANDSHAKE: Byte = 1
    const val TYPE_HANDSHAKE_ACK: Byte = 2
    const val TYPE_MESSAGE: Byte = 3

    private const val MAGIC_0: Byte = 'L'.code.toByte()
    private const val MAGIC_1: Byte = 'M'.code.toByte()
    private const val NONCE_SIZE = 16
    private const val HMAC_SIZE = 32

    fun encodeHandshake(secret: ByteArray, timestampMillis: Long, nonce: ByteArray): ByteArray {
        require(nonce.size == NONCE_SIZE) { "Nonce must be $NONCE_SIZE bytes" }
        val body = handshakeBody(timestampMillis, nonce)
        val hmac = Hmac.sign(secret, body)
        val buffer = ByteBuffer.allocate(2 + body.size + hmac.size)
        buffer.put(MAGIC_0)
        buffer.put(MAGIC_1)
        buffer.put(body)
        buffer.put(hmac)
        return buffer.array()
    }

    fun encodeHandshakeAck(secret: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == NONCE_SIZE) { "Nonce must be $NONCE_SIZE bytes" }
        val body = handshakeAckBody(nonce)
        val hmac = Hmac.sign(secret, body)
        val buffer = ByteBuffer.allocate(2 + body.size + hmac.size)
        buffer.put(MAGIC_0)
        buffer.put(MAGIC_1)
        buffer.put(body)
        buffer.put(hmac)
        return buffer.array()
    }

    fun encodeMessage(secret: ByteArray, channelId: String, payload: ByteArray): ByteArray {
        val channelBytes = channelId.toByteArray(StandardCharsets.UTF_8)
        val body = messageBody(channelBytes, payload)
        val hmac = Hmac.sign(secret, body)
        val buffer = ByteBuffer.allocate(2 + body.size + hmac.size)
        buffer.put(MAGIC_0)
        buffer.put(MAGIC_1)
        buffer.put(body)
        buffer.put(hmac)
        return buffer.array()
    }

    fun decode(packet: ByteArray): MessagingPacket? {
        if (packet.size < 4) {
            return null
        }
        val buffer = ByteBuffer.wrap(packet)
        if (buffer.get() != MAGIC_0 || buffer.get() != MAGIC_1) {
            return null
        }
        val version = buffer.get()
        if (version != VERSION) {
            return null
        }
        val type = buffer.get()
        return when (type) {
            TYPE_HANDSHAKE -> decodeHandshake(buffer)
            TYPE_HANDSHAKE_ACK -> decodeHandshakeAck(buffer)
            TYPE_MESSAGE -> decodeMessage(buffer)
            else -> null
        }
    }

    fun verifyHandshake(packet: HandshakePacket, secret: ByteArray): Boolean {
        val body = handshakeBody(packet.timestampMillis, packet.nonce)
        return Hmac.verify(secret, body, packet.hmac)
    }

    fun verifyHandshakeAck(packet: HandshakeAckPacket, secret: ByteArray): Boolean {
        val body = handshakeAckBody(packet.nonce)
        return Hmac.verify(secret, body, packet.hmac)
    }

    fun verifyMessage(packet: MessagePacket, secret: ByteArray): Boolean {
        val body = messageBody(packet.channelId.toByteArray(StandardCharsets.UTF_8), packet.payload)
        return Hmac.verify(secret, body, packet.hmac)
    }

    private fun handshakeBody(timestampMillis: Long, nonce: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 1 + 8 + NONCE_SIZE)
        buffer.put(VERSION)
        buffer.put(TYPE_HANDSHAKE)
        buffer.putLong(timestampMillis)
        buffer.put(nonce)
        return buffer.array()
    }

    private fun handshakeAckBody(nonce: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 1 + NONCE_SIZE)
        buffer.put(VERSION)
        buffer.put(TYPE_HANDSHAKE_ACK)
        buffer.put(nonce)
        return buffer.array()
    }

    private fun messageBody(channelIdBytes: ByteArray, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 1 + 4 + channelIdBytes.size + 4 + payload.size)
        buffer.put(VERSION)
        buffer.put(TYPE_MESSAGE)
        buffer.putInt(channelIdBytes.size)
        buffer.put(channelIdBytes)
        buffer.putInt(payload.size)
        buffer.put(payload)
        return buffer.array()
    }

    private fun decodeHandshake(buffer: ByteBuffer): HandshakePacket? {
        if (buffer.remaining() != 8 + NONCE_SIZE + HMAC_SIZE) {
            return null
        }
        val timestamp = buffer.long
        val nonce = ByteArray(NONCE_SIZE)
        buffer.get(nonce)
        val hmac = ByteArray(HMAC_SIZE)
        buffer.get(hmac)
        return HandshakePacket(timestamp, nonce, hmac)
    }

    private fun decodeHandshakeAck(buffer: ByteBuffer): HandshakeAckPacket? {
        if (buffer.remaining() != NONCE_SIZE + HMAC_SIZE) {
            return null
        }
        val nonce = ByteArray(NONCE_SIZE)
        buffer.get(nonce)
        val hmac = ByteArray(HMAC_SIZE)
        buffer.get(hmac)
        return HandshakeAckPacket(nonce, hmac)
    }

    private fun decodeMessage(buffer: ByteBuffer): MessagePacket? {
        if (buffer.remaining() < 4 + 4 + HMAC_SIZE) {
            return null
        }
        val channelLength = buffer.int
        if (channelLength <= 0 || channelLength > 1024) {
            return null
        }
        if (buffer.remaining() < channelLength + 4 + HMAC_SIZE) {
            return null
        }
        val channelBytes = ByteArray(channelLength)
        buffer.get(channelBytes)
        val payloadLength = buffer.int
        if (payloadLength < 0 || buffer.remaining() != payloadLength + HMAC_SIZE) {
            return null
        }
        val payload = ByteArray(payloadLength)
        buffer.get(payload)
        val hmac = ByteArray(HMAC_SIZE)
        buffer.get(hmac)
        val channelId = String(channelBytes, StandardCharsets.UTF_8)
        return MessagePacket(channelId, payload, hmac)
    }
}

sealed class MessagingPacket {
    abstract val type: Byte
}

data class HandshakePacket(
    val timestampMillis: Long,
    val nonce: ByteArray,
    val hmac: ByteArray,
) : MessagingPacket() {
    override val type: Byte = MessagingProtocol.TYPE_HANDSHAKE
}

data class HandshakeAckPacket(
    val nonce: ByteArray,
    val hmac: ByteArray,
) : MessagingPacket() {
    override val type: Byte = MessagingProtocol.TYPE_HANDSHAKE_ACK
}

data class MessagePacket(
    val channelId: String,
    val payload: ByteArray,
    val hmac: ByteArray,
) : MessagingPacket() {
    override val type: Byte = MessagingProtocol.TYPE_MESSAGE
}
