/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.token

import ru.hytalemodding.lineage.shared.crypto.Hmac
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val PAYLOAD_DELIMITER = '|'

/**
 * Current transfer token schema version.
 */
const val CURRENT_TRANSFER_TOKEN_VERSION = 1

/**
 * Immutable transfer token claims used for proxy routing.
 *
 * @property version Schema version of the token.
 * @property playerId Stable player identifier for routing.
 * @property targetServerId Backend server identifier selected by the issuer.
 * @property issuedAtMillis Time of issuance in milliseconds since Unix Epoch.
 * @property expiresAtMillis Expiration time in milliseconds since Unix Epoch.
 */
data class TransferToken(
    val version: Int,
    val playerId: String,
    val targetServerId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
) {
    init {
        require(version > 0) { "Token version must be positive" }
        require(playerId.isNotBlank()) { "playerId must not be blank" }
        require(targetServerId.isNotBlank()) { "targetServerId must not be blank" }
        require(!playerId.contains(PAYLOAD_DELIMITER)) { "playerId must not contain '|'" }
        require(!targetServerId.contains(PAYLOAD_DELIMITER)) { "targetServerId must not contain '|'" }
        require(issuedAtMillis >= 0) { "issuedAtMillis must be >= 0" }
        require(expiresAtMillis >= issuedAtMillis) { "expiresAtMillis must be >= issuedAtMillis" }
    }
}

/**
 * Parsed transfer token along with its signature and payload bytes.
 */
data class ParsedTransferToken(
    val token: TransferToken,
    val payloadBytes: ByteArray,
    val signature: ByteArray,
)

/**
 * Thrown when a transfer token string cannot be parsed.
 */
class TransferTokenFormatException(message: String) : IllegalArgumentException(message)

/**
 * Encoding/decoding for transfer tokens using HMAC-SHA256 signatures.
 *
 * Format: `t1.<payload_b64url>.<signature_b64url>`
 * Payload: `version|playerId|targetServerId|issuedAtMillis|expiresAtMillis`
 */
object TransferTokenCodec {
    private const val PREFIX = "t1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /**
     * Encodes [token] into a compact string and signs it with [secret].
     */
    fun encode(token: TransferToken, secret: ByteArray): String {
        require(token.version == CURRENT_TRANSFER_TOKEN_VERSION) {
            "Unsupported token version for encoding: ${token.version}"
        }
        val payloadBytes = buildPayload(token)
        val signature = Hmac.sign(secret, payloadBytes)
        val payloadB64 = encoder.encodeToString(payloadBytes)
        val signatureB64 = encoder.encodeToString(signature)
        return "$PREFIX.$payloadB64.$signatureB64"
    }

    /**
     * Decodes a compact token string into [ParsedTransferToken].
     */
    fun decode(encoded: String): ParsedTransferToken {
        val parts = encoded.split('.')
        if (parts.size != 3) {
            throw TransferTokenFormatException("Token must have 3 parts")
        }
        if (parts[0] != PREFIX) {
            throw TransferTokenFormatException("Unsupported token prefix: ${parts[0]}")
        }
        val payloadBytes = decodePart(parts[1], "payload")
        val signatureBytes = decodePart(parts[2], "signature")
        val token = parsePayload(payloadBytes)
        return ParsedTransferToken(token, payloadBytes, signatureBytes)
    }

    /**
     * Verifies signature for [parsed] using [secret].
     */
    fun verifySignature(parsed: ParsedTransferToken, secret: ByteArray): Boolean {
        return Hmac.verify(secret, parsed.payloadBytes, parsed.signature)
    }

    private fun buildPayload(token: TransferToken): ByteArray {
        val payload = buildString {
            append(token.version)
            append(PAYLOAD_DELIMITER)
            append(token.playerId)
            append(PAYLOAD_DELIMITER)
            append(token.targetServerId)
            append(PAYLOAD_DELIMITER)
            append(token.issuedAtMillis)
            append(PAYLOAD_DELIMITER)
            append(token.expiresAtMillis)
        }
        return payload.toByteArray(StandardCharsets.UTF_8)
    }

    private fun parsePayload(payloadBytes: ByteArray): TransferToken {
        val payload = payloadBytes.toString(StandardCharsets.UTF_8)
        val parts = payload.split(PAYLOAD_DELIMITER)
        if (parts.size < 5) {
            throw TransferTokenFormatException("Payload must have at least 5 parts")
        }
        val version = parts[0].toIntOrNull()
            ?: throw TransferTokenFormatException("Invalid token version")
        val issuedAt = parts[3].toLongOrNull()
            ?: throw TransferTokenFormatException("Invalid issuedAtMillis")
        val expiresAt = parts[4].toLongOrNull()
            ?: throw TransferTokenFormatException("Invalid expiresAtMillis")
        return TransferToken(
            version = version,
            playerId = parts[1],
            targetServerId = parts[2],
            issuedAtMillis = issuedAt,
            expiresAtMillis = expiresAt,
        )
    }

    private fun decodePart(part: String, label: String): ByteArray {
        if (part.isBlank()) {
            throw TransferTokenFormatException("$label part must not be blank")
        }
        return try {
            decoder.decode(part)
        } catch (ex: IllegalArgumentException) {
            throw TransferTokenFormatException("Invalid base64 for $label part")
        }
    }
}
