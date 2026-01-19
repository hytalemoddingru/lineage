/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.handshake

import ru.hytalemodding.lineage.backend.security.ReplayProtector
import ru.hytalemodding.lineage.backend.security.TokenValidator
import ru.hytalemodding.lineage.backend.security.ValidatedProxyToken
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.TokenValidationError
import ru.hytalemodding.lineage.shared.token.TokenValidationException
import java.nio.charset.StandardCharsets

/**
 * Extracts and validates proxy tokens during the handshake phase.
 *
 * This class is designed to be called from a server hook that has access to
 * the Connect packet referral data.
 */
class HandshakeInterceptor(
    private val tokenValidator: TokenValidator,
    private val expectedServerId: String,
    private val replayProtector: ReplayProtector,
) {
    init {
        require(expectedServerId.isNotBlank()) { "expectedServerId must not be blank" }
    }

    /**
     * Validates the proxy token stored in [referralData].
     *
     * @throws TokenValidationException if token is missing or invalid.
     */
    fun validateReferralData(referralData: ByteArray?): ProxyToken {
        if (referralData == null || referralData.isEmpty()) {
            throw TokenValidationException(TokenValidationError.MALFORMED, "Missing proxy token referral data")
        }
        val token = String(referralData, StandardCharsets.UTF_8)
            .trim { it <= ' ' || it == '\u0000' }
        if (token.isBlank()) {
            throw TokenValidationException(TokenValidationError.MALFORMED, "Empty proxy token referral data")
        }
        val validated = tokenValidator.validate(token, expectedServerId)
        enforceReplayProtection(validated)
        return validated.token
    }

    private fun enforceReplayProtection(validated: ValidatedProxyToken) {
        if (!replayProtector.tryRegister(validated.replayKey)) {
            throw TokenValidationException(TokenValidationError.REPLAYED, "Replay detected for ${validated.token.playerId}")
        }
    }
}
