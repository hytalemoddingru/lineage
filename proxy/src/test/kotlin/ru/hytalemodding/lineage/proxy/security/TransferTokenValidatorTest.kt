/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.security

import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.token.CURRENT_TRANSFER_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.TransferToken
import ru.hytalemodding.lineage.shared.token.TransferTokenCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TransferTokenValidatorTest {
    private val secret = "secret-123".toByteArray()

    @Test
    fun validatesTransferToken() {
        val clock = FixedClock(1_500L)
        val validator = TransferTokenValidator(secret, clock)
        val token = TransferToken(
            version = CURRENT_TRANSFER_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val encoded = TransferTokenCodec.encode(token, secret)

        val parsed = validator.tryValidate(encoded, "player-1")

        assertEquals("hub", parsed?.targetServerId)
    }

    @Test
    fun rejectsInvalidSignature() {
        val clock = FixedClock(1_500L)
        val validator = TransferTokenValidator(secret, clock)
        val token = TransferToken(
            version = CURRENT_TRANSFER_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val encoded = TransferTokenCodec.encode(token, "wrong-secret".toByteArray())

        assertNull(validator.tryValidate(encoded, "player-1"))
    }

    @Test
    fun rejectsExpiredToken() {
        val clock = FixedClock(3_000L)
        val validator = TransferTokenValidator(secret, clock)
        val token = TransferToken(
            version = CURRENT_TRANSFER_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val encoded = TransferTokenCodec.encode(token, secret)

        assertNull(validator.tryValidate(encoded, "player-1"))
    }

    @Test
    fun rejectsPlayerMismatch() {
        val clock = FixedClock(1_500L)
        val validator = TransferTokenValidator(secret, clock)
        val token = TransferToken(
            version = CURRENT_TRANSFER_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val encoded = TransferTokenCodec.encode(token, secret)

        assertNull(validator.tryValidate(encoded, "player-2"))
    }

    @Test
    fun ignoresNonTransferTokens() {
        val clock = FixedClock(1_500L)
        val validator = TransferTokenValidator(secret, clock)

        assertNull(validator.tryValidate("v1.payload.signature", "player-1"))
        assertFalse(validator.isTransferTokenCandidate("v1.payload.signature"))
    }

    @Test
    fun rejectsReplayedTransferToken() {
        val clock = FixedClock(1_500L)
        val validator = TransferTokenValidator(secret, clock)
        val token = TransferToken(
            version = CURRENT_TRANSFER_TOKEN_VERSION,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val encoded = TransferTokenCodec.encode(token, secret)

        val first = validator.tryValidate(encoded, "player-1")
        val second = validator.tryValidate(encoded, "player-1")

        assertEquals("hub", first?.targetServerId)
        assertNull(second)
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
