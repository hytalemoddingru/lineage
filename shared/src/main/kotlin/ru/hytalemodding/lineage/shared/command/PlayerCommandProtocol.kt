/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.command

import ru.hytalemodding.lineage.shared.control.ControlProtocol
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * String-based protocol for proxy player commands over messaging.
 *
 * Envelope v2:
 * version, playerId, issuedAtMillis, ttlMillis, nonceB64, message
 */
object PlayerCommandProtocol {
    const val REQUEST_CHANNEL_ID = "lineage.command"
    const val RESPONSE_CHANNEL_ID = "lineage.command.response"
    const val SYSTEM_RESPONSE_CHANNEL_ID = "lineage.system.response"
    const val DEFAULT_TTL_MILLIS = 10_000L
    const val VERSION = "v2"
    const val MAX_MESSAGE_BYTES = 2048
    const val MAX_PAYLOAD_BYTES = 4096

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun peekVersion(payload: ByteArray): String? {
        val text = payload.toString(StandardCharsets.UTF_8)
        val line = text.substringBefore('\n', "")
        if (line.isBlank()) {
            return null
        }
        return line
    }

    fun hasSupportedVersion(payload: ByteArray): Boolean {
        return peekVersion(payload) == VERSION
    }

    fun encodeRequest(
        playerId: UUID,
        command: String,
        issuedAtMillis: Long = System.currentTimeMillis(),
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        nonceB64: String = nextNonceB64(),
    ): ByteArray {
        val sanitized = sanitizeRequest(command)
        require(sanitized.isNotBlank()) { "command must not be blank" }
        require(sanitized.toByteArray(StandardCharsets.UTF_8).size <= MAX_MESSAGE_BYTES) {
            "command exceeds max size $MAX_MESSAGE_BYTES bytes"
        }
        val payload = "$VERSION\n$playerId\n$issuedAtMillis\n$ttlMillis\n$nonceB64\n$sanitized"
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "request payload exceeds max size $MAX_PAYLOAD_BYTES bytes" }
        return bytes
    }

    fun encodeResponse(
        playerId: UUID,
        message: String,
        issuedAtMillis: Long = System.currentTimeMillis(),
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        nonceB64: String = nextNonceB64(),
    ): ByteArray {
        val sanitized = sanitizeResponse(message)
        require(sanitized.isNotBlank()) { "message must not be blank" }
        require(sanitized.toByteArray(StandardCharsets.UTF_8).size <= MAX_MESSAGE_BYTES) {
            "message exceeds max size $MAX_MESSAGE_BYTES bytes"
        }
        val payload = "$VERSION\n$playerId\n$issuedAtMillis\n$ttlMillis\n$nonceB64\n$sanitized"
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "response payload exceeds max size $MAX_PAYLOAD_BYTES bytes" }
        return bytes
    }

    fun decodeRequest(payload: ByteArray): PlayerCommandRequest? {
        val parts = split(payload) ?: return null
        val playerId = parseUuid(parts.playerId) ?: return null
        if (parts.message.isBlank()) {
            return null
        }
        return PlayerCommandRequest(
            playerId = playerId,
            command = parts.message,
            issuedAtMillis = parts.issuedAtMillis,
            ttlMillis = parts.ttlMillis,
            nonce = parts.nonce,
        )
    }

    fun decodeResponse(payload: ByteArray): PlayerCommandResponse? {
        val parts = split(payload) ?: return null
        val playerId = parseUuid(parts.playerId) ?: return null
        if (parts.message.isBlank()) {
            return null
        }
        return PlayerCommandResponse(
            playerId = playerId,
            message = parts.message,
            issuedAtMillis = parts.issuedAtMillis,
            ttlMillis = parts.ttlMillis,
            nonce = parts.nonce,
        )
    }

    private fun split(payload: ByteArray): PayloadParts? {
        if (payload.size > MAX_PAYLOAD_BYTES) {
            return null
        }
        val text = payload.toString(StandardCharsets.UTF_8)
        val lines = text.split('\n', limit = 6)
        if (lines.size != 6) {
            return null
        }
        val version = lines[0]
        if (version != VERSION) {
            return null
        }
        val playerId = lines[1].trim()
        val issuedAtMillis = lines[2].trim().toLongOrNull() ?: return null
        val ttlMillis = lines[3].trim().toLongOrNull() ?: return null
        if (ttlMillis <= 0) {
            return null
        }
        val nonce = parseNonce(lines[4].trim()) ?: return null
        val message = lines[5].trim()
        if (playerId.isBlank()) {
            return null
        }
        return PayloadParts(playerId, issuedAtMillis, ttlMillis, nonce, message)
    }

    private fun parseNonce(value: String): ByteArray? {
        if (value.isBlank()) {
            return null
        }
        val decoded = runCatching { decoder.decode(value) }.getOrNull() ?: return null
        if (decoded.size != ControlProtocol.NONCE_SIZE) {
            return null
        }
        return decoded
    }

    private fun parseUuid(value: String): UUID? {
        return try {
            UUID.fromString(value)
        } catch (ex: IllegalArgumentException) {
            null
        }
    }

    private fun sanitizeRequest(value: String): String {
        return value.replace('\r', ' ').replace('\n', ' ').trim()
    }

    private fun sanitizeResponse(value: String): String {
        return value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
    }

    private fun nextNonceB64(): String {
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE)
        random.nextBytes(nonce)
        return encoder.encodeToString(nonce)
    }

    private data class PayloadParts(
        val playerId: String,
        val issuedAtMillis: Long,
        val ttlMillis: Long,
        val nonce: ByteArray,
        val message: String,
    )
}

data class PlayerCommandRequest(
    val playerId: UUID,
    val command: String,
    val issuedAtMillis: Long,
    val ttlMillis: Long,
    val nonce: ByteArray,
)

data class PlayerCommandResponse(
    val playerId: UUID,
    val message: String,
    val issuedAtMillis: Long,
    val ttlMillis: Long,
    val nonce: ByteArray,
)
