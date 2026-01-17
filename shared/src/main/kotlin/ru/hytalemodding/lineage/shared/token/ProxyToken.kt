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
 * Current token schema version.
 */
const val CURRENT_PROXY_TOKEN_VERSION = 1

/**
 * Immutable token claims used by proxy and backend validation.
 *
 * @property version Schema version of the token.
 * @property playerId Stable player identifier for routing and auditing.
 * @property targetServerId Backend server identifier selected by the proxy.
 * @property issuedAtMillis Time of issuance in milliseconds since Unix Epoch.
 * @property expiresAtMillis Expiration time in milliseconds since Unix Epoch.
 */
data class ProxyToken(
    val version: Int,
    val playerId: String,
    val targetServerId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val clientCertB64: String? = null,
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
 * Parsed token along with its signature and payload bytes.
 *
 * @property token Parsed token claims.
 * @property payloadBytes Raw payload bytes used for signature verification.
 * @property signature Raw signature bytes from the token.
 */
data class ParsedProxyToken(
    val token: ProxyToken,
    val payloadBytes: ByteArray,
    val signature: ByteArray,
)

/**
 * Thrown when a token string cannot be parsed.
 */
class ProxyTokenFormatException(message: String) : IllegalArgumentException(message)

/**
 * Encoding/decoding for proxy tokens using HMAC-SHA256 signatures.
 *
 * Format: `v1.<payload_b64url>.<signature_b64url>`
 * Payload: `version|playerId|targetServerId|issuedAtMillis|expiresAtMillis`
 */
object ProxyTokenCodec {
    private const val PREFIX = "v1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /**
     * Encodes [token] into a compact string and signs it with [secret].
     */
    fun encode(token: ProxyToken, secret: ByteArray): String {
        require(token.version == CURRENT_PROXY_TOKEN_VERSION) {
            "Unsupported token version for encoding: ${token.version}"
        }
        val payloadBytes = buildPayload(token)
        val signature = Hmac.sign(secret, payloadBytes)
        val payloadB64 = encoder.encodeToString(payloadBytes)
        val signatureB64 = encoder.encodeToString(signature)
        return "$PREFIX.$payloadB64.$signatureB64"
    }

    /**
     * Decodes a compact token string into [ParsedProxyToken].
     */
    fun decode(encoded: String): ParsedProxyToken {
        val parts = encoded.split('.')
        if (parts.size != 3) {
            throw ProxyTokenFormatException("Token must have 3 parts")
        }
        if (parts[0] != PREFIX) {
            throw ProxyTokenFormatException("Unsupported token prefix: ${parts[0]}")
        }
        val payloadBytes = decodePart(parts[1], "payload")
        val signatureBytes = decodePart(parts[2], "signature")
        val token = parsePayload(payloadBytes)
        return ParsedProxyToken(token, payloadBytes, signatureBytes)
    }

    /**
     * Verifies signature for [parsed] using [secret].
     */
    fun verifySignature(parsed: ParsedProxyToken, secret: ByteArray): Boolean {
        return Hmac.verify(secret, parsed.payloadBytes, parsed.signature)
    }

    private fun buildPayload(token: ProxyToken): ByteArray {
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
            append(PAYLOAD_DELIMITER)
            append(token.clientCertB64 ?: "")
        }
        return payload.toByteArray(StandardCharsets.UTF_8)
    }

    private fun parsePayload(payloadBytes: ByteArray): ProxyToken {
        val payload = payloadBytes.toString(StandardCharsets.UTF_8)
        val parts = payload.split(PAYLOAD_DELIMITER)
        if (parts.size < 5) {
            throw ProxyTokenFormatException("Payload must have at least 5 parts")
        }
        val version = parts[0].toIntOrNull()
            ?: throw ProxyTokenFormatException("Invalid token version")
        val issuedAt = parts[3].toLongOrNull()
            ?: throw ProxyTokenFormatException("Invalid issuedAtMillis")
        val expiresAt = parts[4].toLongOrNull()
            ?: throw ProxyTokenFormatException("Invalid expiresAtMillis")
        val clientCert = if (parts.size > 5 && parts[5].isNotBlank()) parts[5] else null
        return ProxyToken(
            version = version,
            playerId = parts[1],
            targetServerId = parts[2],
            issuedAtMillis = issuedAt,
            expiresAtMillis = expiresAt,
            clientCertB64 = clientCert,
        )
    }

    private fun decodePart(part: String, label: String): ByteArray {
        if (part.isBlank()) {
            throw ProxyTokenFormatException("$label part must not be blank")
        }
        return try {
            decoder.decode(part)
        } catch (ex: IllegalArgumentException) {
            throw ProxyTokenFormatException("Invalid base64 for $label part")
        }
    }
}
