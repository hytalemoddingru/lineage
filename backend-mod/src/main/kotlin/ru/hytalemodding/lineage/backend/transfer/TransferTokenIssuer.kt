/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.transfer

import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import ru.hytalemodding.lineage.shared.token.CURRENT_TRANSFER_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.TransferToken
import ru.hytalemodding.lineage.shared.token.TransferTokenCodec
import java.nio.charset.StandardCharsets

/**
 * Issues transfer tokens for cross-server referrals.
 */
class TransferTokenIssuer(
    private val secret: ByteArray,
    private val tokenTtlMillis: Long = DEFAULT_TRANSFER_TOKEN_TTL_MILLIS,
    private val clock: Clock = SystemClock,
) {
    init {
        require(secret.isNotEmpty()) { "Transfer token secret must not be empty" }
        require(tokenTtlMillis > 0) { "tokenTtlMillis must be > 0" }
    }

    /**
     * Issues a signed transfer token for [playerId] and [targetServerId].
     */
    fun issue(playerId: String, targetServerId: String): String {
        val now = clock.nowMillis()
        val token = TransferToken(
            version = CURRENT_TRANSFER_TOKEN_VERSION,
            playerId = playerId,
            targetServerId = targetServerId,
            issuedAtMillis = now,
            expiresAtMillis = now + tokenTtlMillis,
        )
        return TransferTokenCodec.encode(token, secret)
    }

    /**
     * Issues referral data payload ready to embed into a Connect packet.
     */
    fun issueReferralData(playerId: String, targetServerId: String): ByteArray {
        return issue(playerId, targetServerId).toByteArray(StandardCharsets.UTF_8)
    }

    companion object {
        /**
         * Default transfer token TTL in milliseconds.
         */
        const val DEFAULT_TRANSFER_TOKEN_TTL_MILLIS = 30_000L
    }
}
