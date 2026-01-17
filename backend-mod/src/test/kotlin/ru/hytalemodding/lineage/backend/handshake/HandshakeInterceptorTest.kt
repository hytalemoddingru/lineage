/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.handshake

import ru.hytalemodding.lineage.backend.security.TokenValidator
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.ProxyTokenCodec
import ru.hytalemodding.lineage.shared.token.TokenValidationError
import ru.hytalemodding.lineage.shared.token.TokenValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HandshakeInterceptorTest {
    private val secret = "secret-123".toByteArray()

    @Test
    fun validatesReferralToken() {
        val validator = TokenValidator(secret, FixedClock(1_500L))
        val interceptor = HandshakeInterceptor(validator, "hub")
        val token = ProxyToken(
            version = 1,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val encoded = ProxyTokenCodec.encode(token, secret)

        val parsed = interceptor.validateReferralData(encoded.toByteArray())

        assertEquals("player-1", parsed.playerId)
    }

    @Test
    fun rejectsMissingReferralData() {
        val validator = TokenValidator(secret, FixedClock(1_500L))
        val interceptor = HandshakeInterceptor(validator, "hub")

        val ex = assertThrows(TokenValidationException::class.java) {
            interceptor.validateReferralData(null)
        }
        assertEquals(TokenValidationError.MALFORMED, ex.error)
    }

    @Test
    fun rejectsBlankReferralData() {
        val validator = TokenValidator(secret, FixedClock(1_500L))
        val interceptor = HandshakeInterceptor(validator, "hub")

        val ex = assertThrows(TokenValidationException::class.java) {
            interceptor.validateReferralData("   ".toByteArray())
        }
        assertEquals(TokenValidationError.MALFORMED, ex.error)
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
