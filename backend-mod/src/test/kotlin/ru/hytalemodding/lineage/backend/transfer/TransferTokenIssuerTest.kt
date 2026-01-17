/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.transfer

import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.token.TransferTokenCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransferTokenIssuerTest {
    private val secret = "secret-123".toByteArray()

    @Test
    fun issuesSignedTransferTokenWithTtl() {
        val clock = FixedClock(1_000L)
        val issuer = TransferTokenIssuer(secret, tokenTtlMillis = 5_000L, clock = clock)

        val encoded = issuer.issue("player-1", "hub")
        val parsed = TransferTokenCodec.decode(encoded)

        assertEquals("player-1", parsed.token.playerId)
        assertEquals("hub", parsed.token.targetServerId)
        assertEquals(1_000L, parsed.token.issuedAtMillis)
        assertEquals(6_000L, parsed.token.expiresAtMillis)
        assertTrue(TransferTokenCodec.verifySignature(parsed, secret))
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
