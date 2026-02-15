/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.control

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object ControlProtocol {
    const val VERSION: Byte = 1
    const val MAGIC_0: Byte = 0x4C
    const val MAGIC_1: Byte = 0x43
    const val NONCE_SIZE = 16
    const val MAX_SENDER_ID_LENGTH = 64
    const val MAX_SKEW_MILLIS = 120_000L

    private const val TYPE_TRANSFER_REQUEST: Byte = 1
    private const val TYPE_TRANSFER_RESULT: Byte = 2
    private const val TYPE_TOKEN_VALIDATION: Byte = 3
    private const val TYPE_BACKEND_STATUS: Byte = 4

    fun encode(envelope: ControlEnvelope): ByteArray {
        require(envelope.version == VERSION) { "Unsupported control protocol version: ${envelope.version}" }
        require(envelope.senderId.isNotBlank()) { "senderId must not be blank" }
        val senderIdBytes = envelope.senderId.toByteArray(StandardCharsets.UTF_8)
        require(senderIdBytes.size <= MAX_SENDER_ID_LENGTH) { "senderId exceeds max length $MAX_SENDER_ID_LENGTH" }
        require(envelope.nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes" }
        require(envelope.payload.size >= 0) { "payload must not be null" }
        val typeByte = encodeType(envelope.type)
        val buffer = ByteBuffer.allocate(
            2 + 1 + 1 + 1 + senderIdBytes.size + 8 + 8 + NONCE_SIZE + 4 + envelope.payload.size
        )
        buffer.put(MAGIC_0)
        buffer.put(MAGIC_1)
        buffer.put(VERSION)
        buffer.put(typeByte)
        buffer.put(senderIdBytes.size.toByte())
        buffer.put(senderIdBytes)
        buffer.putLong(envelope.issuedAtMillis)
        buffer.putLong(envelope.ttlMillis)
        buffer.put(envelope.nonce)
        buffer.putInt(envelope.payload.size)
        buffer.put(envelope.payload)
        return buffer.array()
    }

    fun decode(packet: ByteArray): ControlEnvelope? {
        if (packet.size < 2 + 1 + 1 + 1 + 8 + 8 + NONCE_SIZE + 4) {
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
        val type = decodeType(buffer.get()) ?: return null
        val senderIdLen = buffer.get().toInt() and 0xFF
        if (senderIdLen !in 1..MAX_SENDER_ID_LENGTH) {
            return null
        }
        if (buffer.remaining() < senderIdLen + 8 + 8 + NONCE_SIZE + 4) {
            return null
        }
        val senderIdBytes = ByteArray(senderIdLen)
        buffer.get(senderIdBytes)
        val senderId = senderIdBytes.toString(StandardCharsets.UTF_8)
        val issuedAtMillis = buffer.long
        val ttlMillis = buffer.long
        if (buffer.remaining() < NONCE_SIZE + 4) {
            return null
        }
        val nonce = ByteArray(NONCE_SIZE)
        buffer.get(nonce)
        val payloadLength = buffer.int
        if (payloadLength < 0 || buffer.remaining() != payloadLength) {
            return null
        }
        val payload = ByteArray(payloadLength)
        buffer.get(payload)
        return ControlEnvelope(
            version = version,
            type = type,
            senderId = senderId,
            issuedAtMillis = issuedAtMillis,
            ttlMillis = ttlMillis,
            nonce = nonce,
            payload = payload,
        )
    }

    fun isTimestampValid(issuedAtMillis: Long, ttlMillis: Long, nowMillis: Long, maxSkewMillis: Long = MAX_SKEW_MILLIS): Boolean {
        if (ttlMillis <= 0) {
            return false
        }
        val lowerBound = issuedAtMillis - maxSkewMillis
        val upperBound = issuedAtMillis + ttlMillis + maxSkewMillis
        return nowMillis in lowerBound..upperBound
    }

    private fun encodeType(type: ControlMessageType): Byte {
        return when (type) {
            ControlMessageType.TRANSFER_REQUEST -> TYPE_TRANSFER_REQUEST
            ControlMessageType.TRANSFER_RESULT -> TYPE_TRANSFER_RESULT
            ControlMessageType.TOKEN_VALIDATION -> TYPE_TOKEN_VALIDATION
            ControlMessageType.BACKEND_STATUS -> TYPE_BACKEND_STATUS
        }
    }

    private fun decodeType(value: Byte): ControlMessageType? {
        return when (value) {
            TYPE_TRANSFER_REQUEST -> ControlMessageType.TRANSFER_REQUEST
            TYPE_TRANSFER_RESULT -> ControlMessageType.TRANSFER_RESULT
            TYPE_TOKEN_VALIDATION -> ControlMessageType.TOKEN_VALIDATION
            TYPE_BACKEND_STATUS -> ControlMessageType.BACKEND_STATUS
            else -> null
        }
    }
}
