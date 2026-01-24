/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.security

import ru.hytalemodding.lineage.shared.token.CURRENT_PROXY_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.ProxyTokenCodec
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import java.security.SecureRandom
import java.util.Base64

/**
 * Issues signed proxy tokens for handshake metadata.
 */
class TokenService(
    private val secret: ByteArray,
    private val tokenTtlMillis: Long,
    private val clock: Clock = SystemClock,
) {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    init {
        require(secret.isNotEmpty()) { "Proxy secret must not be empty" }
        require(tokenTtlMillis > 0) { "tokenTtlMillis must be > 0" }
    }

    /**
     * Issues a signed proxy token for the given [playerId] and [targetServerId].
     */
    fun issueToken(
        playerId: String,
        targetServerId: String,
        clientCertB64: String? = null,
        proxyCertB64: String? = null,
    ): String {
        val now = clock.nowMillis()
        val nonceB64 = generateNonce()
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = playerId,
            targetServerId = targetServerId,
            issuedAtMillis = now,
            expiresAtMillis = now + tokenTtlMillis,
            clientCertB64 = clientCertB64,
            proxyCertB64 = proxyCertB64,
            nonceB64 = nonceB64,
        )
        return ProxyTokenCodec.encode(token, secret)
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(NONCE_SIZE)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    private companion object {
        private const val NONCE_SIZE = 16
    }
}
