/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.security

import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import ru.hytalemodding.lineage.shared.token.CURRENT_PROXY_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.ParsedProxyToken
import ru.hytalemodding.lineage.shared.token.ProxyTokenCodec
import ru.hytalemodding.lineage.shared.token.ProxyTokenFormatException
import ru.hytalemodding.lineage.shared.token.TokenValidationError
import ru.hytalemodding.lineage.shared.token.TokenValidationException
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Validates proxy tokens received by the backend server, supporting secret rotation.
 */
class TokenValidator(
    private val secrets: List<ByteArray>,
    private val clock: Clock = SystemClock,
) {
    private val logger = LoggerFactory.getLogger(TokenValidator::class.java)

    constructor(secret: ByteArray, clock: Clock = SystemClock) : this(listOf(secret), clock)

    init {
        require(secrets.isNotEmpty()) { "At least one proxy secret must be provided" }
        secrets.forEachIndexed { index, secret ->
            require(secret.isNotEmpty()) { "Proxy secret at index $index must not be empty" }
        }
    }

    /**
     * Validates [encodedToken] and returns parsed token claims on success.
     *
     * @throws TokenValidationException when token is invalid or expired.
     */
    fun validate(encodedToken: String, expectedServerId: String): ValidatedProxyToken {
        val parsed = try {
            ProxyTokenCodec.decode(encodedToken)
        } catch (ex: ProxyTokenFormatException) {
            val parts = encodedToken.split('.')
            val payloadLen = parts.getOrNull(1)?.length ?: -1
            logger.warn(
                "Malformed proxy token (parts={}, payloadLen={}, len={})",
                parts.size,
                payloadLen,
                encodedToken.length,
            )
            throw TokenValidationException(TokenValidationError.MALFORMED, ex.message ?: "Malformed token")
        }

        if (!hasValidSignature(parsed)) {
            throw TokenValidationException(TokenValidationError.INVALID_SIGNATURE, "Invalid token signature")
        }

        val token = parsed.token
        if (token.version != CURRENT_PROXY_TOKEN_VERSION) {
            throw TokenValidationException(
                TokenValidationError.UNSUPPORTED_VERSION,
                "Unsupported token version: ${token.version}",
            )
        }
        if (token.clientCertB64.isNullOrBlank()) {
            throw TokenValidationException(TokenValidationError.MALFORMED, "Missing client certificate in token")
        }
        if (token.proxyCertB64.isNullOrBlank()) {
            throw TokenValidationException(TokenValidationError.MALFORMED, "Missing proxy certificate in token")
        }

        val now = clock.nowMillis()
        if (token.issuedAtMillis > now) {
            throw TokenValidationException(TokenValidationError.NOT_YET_VALID, "Token issued in the future")
        }
        if (token.expiresAtMillis < now) {
            throw TokenValidationException(TokenValidationError.EXPIRED, "Token has expired")
        }
        if (token.targetServerId != expectedServerId) {
            throw TokenValidationException(
                TokenValidationError.TARGET_MISMATCH,
                "Token target mismatch: ${token.targetServerId}",
            )
        }

        val replayKey = buildReplayKey(parsed, token, expectedServerId)
        return ValidatedProxyToken(token, replayKey)
    }

    private fun hasValidSignature(parsed: ParsedProxyToken): Boolean {
        return secrets.any { secret -> ProxyTokenCodec.verifySignature(parsed, secret) }
    }

    private fun buildReplayKey(parsed: ParsedProxyToken, token: ProxyToken, backendId: String): ReplayKey {
        val nonce = token.nonceB64 ?: Base64.getUrlEncoder().withoutPadding().encodeToString(parsed.signature)
        return ReplayKey(backendId, token.playerId, nonce)
    }
}

data class ValidatedProxyToken(
    val token: ProxyToken,
    val replayKey: ReplayKey,
)
