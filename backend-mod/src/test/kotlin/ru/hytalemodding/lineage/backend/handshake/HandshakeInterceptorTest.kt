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
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.ProxyTokenCodec
import ru.hytalemodding.lineage.shared.token.CURRENT_PROXY_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.TokenValidationError
import ru.hytalemodding.lineage.shared.token.TokenValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HandshakeInterceptorTest {
    private val secret = "secret-123".toByteArray()

    @Test
    fun validatesReferralToken() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)
        val replay = ReplayProtector(10_000L, 1000, clock)
        val interceptor = HandshakeInterceptor(validator, "hub", replay)
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
            nonceB64 = "nonce",
        )
        val encoded = ProxyTokenCodec.encode(token, secret)

        val parsed = interceptor.validateReferralData(encoded.toByteArray())

        assertEquals("player-1", parsed.playerId)
    }

    @Test
    fun rejectsMissingReferralData() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)
        val replay = ReplayProtector(10_000L, 1000, clock)
        val interceptor = HandshakeInterceptor(validator, "hub", replay)

        val ex = assertThrows(TokenValidationException::class.java) {
            interceptor.validateReferralData(null)
        }
        assertEquals(TokenValidationError.MALFORMED, ex.error)
    }

    @Test
    fun rejectsBlankReferralData() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)
        val replay = ReplayProtector(10_000L, 1000, clock)
        val interceptor = HandshakeInterceptor(validator, "hub", replay)

        val ex = assertThrows(TokenValidationException::class.java) {
            interceptor.validateReferralData("   ".toByteArray())
        }
        assertEquals(TokenValidationError.MALFORMED, ex.error)
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    @Test
    fun rejectsReplayedToken() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)
        val replay = ReplayProtector(10_000L, 1000, clock)
        val interceptor = HandshakeInterceptor(validator, "hub", replay)
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
            nonceB64 = "nonce",
        )
        val encoded = ProxyTokenCodec.encode(token, secret).toByteArray()

        interceptor.validateReferralData(encoded)
        val ex = assertThrows(TokenValidationException::class.java) {
            interceptor.validateReferralData(encoded)
        }
        assertEquals(TokenValidationError.REPLAYED, ex.error)
    }
}
