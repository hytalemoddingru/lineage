/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.security

import ru.hytalemodding.lineage.shared.crypto.Hmac
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.ProxyTokenCodec
import ru.hytalemodding.lineage.shared.token.CURRENT_PROXY_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.LEGACY_PROXY_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.TokenValidationError
import ru.hytalemodding.lineage.shared.token.TokenValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Base64

class TokenValidatorTest {
    private val secret = "secret-123".toByteArray()

    @Test
    fun validatesToken() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
            nonceB64 = "nonce",
        )
        val encoded = ProxyTokenCodec.encode(token, secret)

        val parsed = validator.validate(encoded, "hub")

        assertEquals("player-1", parsed.token.playerId)
    }

    @Test
    fun rejectsInvalidSignature() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
            nonceB64 = "nonce",
        )
        val encoded = ProxyTokenCodec.encode(token, "other-secret".toByteArray())

        val ex = assertThrows(TokenValidationException::class.java) {
            validator.validate(encoded, "hub")
        }
        assertEquals(TokenValidationError.INVALID_SIGNATURE, ex.error)
    }

    @Test
    fun rejectsExpiredToken() {
        val clock = FixedClock(3_000L)
        val validator = TokenValidator(secret, clock)
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
            nonceB64 = "nonce",
        )
        val encoded = ProxyTokenCodec.encode(token, secret)

        val ex = assertThrows(TokenValidationException::class.java) {
            validator.validate(encoded, "hub")
        }
        assertEquals(TokenValidationError.EXPIRED, ex.error)
    }

    @Test
    fun rejectsTargetMismatch() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
            nonceB64 = "nonce",
        )
        val encoded = ProxyTokenCodec.encode(token, secret)

        val ex = assertThrows(TokenValidationException::class.java) {
            validator.validate(encoded, "minigame")
        }
        assertEquals(TokenValidationError.TARGET_MISMATCH, ex.error)
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)
        val payload = "99|player-1|hub|1000|2000|nonce".toByteArray()
        val signature = Hmac.sign(secret, payload)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val encoded = "v1.${encoder.encodeToString(payload)}.${encoder.encodeToString(signature)}"

        val ex = assertThrows(TokenValidationException::class.java) {
            validator.validate(encoded, "hub")
        }
        assertEquals(TokenValidationError.UNSUPPORTED_VERSION, ex.error)
    }

    @Test
    fun rejectsMalformedToken() {
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(secret, clock)

        val ex = assertThrows(TokenValidationException::class.java) {
            validator.validate("bad-token", "hub")
        }
        assertEquals(TokenValidationError.MALFORMED, ex.error)
    }

    @Test
    fun acceptsPreviousSecretDuringRotation() {
        val clock = FixedClock(1_500L)
        val previous = "old-secret".toByteArray()
        val validator = TokenValidator(listOf(secret, previous), clock)
        val token = ProxyToken(
            version = LEGACY_PROXY_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val encoded = ProxyTokenCodec.encode(token, previous)

        val parsed = validator.validate(encoded, "hub")

        assertEquals("player-1", parsed.token.playerId)
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
