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

/**
 * Issues signed proxy tokens for handshake metadata.
 */
class TokenService(
    private val secret: ByteArray,
    private val tokenTtlMillis: Long,
    private val clock: Clock = SystemClock,
) {
    init {
        require(secret.isNotEmpty()) { "Proxy secret must not be empty" }
        require(tokenTtlMillis > 0) { "tokenTtlMillis must be > 0" }
    }

    /**
     * Issues a signed proxy token for the given [playerId] and [targetServerId].
     */
    fun issueToken(playerId: String, targetServerId: String, clientCertB64: String? = null): String {
        val now = clock.nowMillis()
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = playerId,
            targetServerId = targetServerId,
            issuedAtMillis = now,
            expiresAtMillis = now + tokenTtlMillis,
            clientCertB64 = clientCertB64,
        )
        return ProxyTokenCodec.encode(token, secret)
    }
}
