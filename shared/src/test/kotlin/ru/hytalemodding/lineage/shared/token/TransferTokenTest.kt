/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.token

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransferTokenTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val token = TransferToken(
            version = CURRENT_TRANSFER_TOKEN_VERSION,
            playerId = "player-123",
            targetServerId = "hub-1",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val secret = "transfer-secret".toByteArray()

        val encoded = TransferTokenCodec.encode(token, secret)
        val parsed = TransferTokenCodec.decode(encoded)

        assertEquals(token, parsed.token)
        assertTrue(TransferTokenCodec.verifySignature(parsed, secret))
        assertFalse(TransferTokenCodec.verifySignature(parsed, "wrong-secret".toByteArray()))
    }

    @Test
    fun decodeRejectsBadPrefix() {
        val bad = "t2.payload.signature"
        assertThrows(TransferTokenFormatException::class.java) {
            TransferTokenCodec.decode(bad)
        }
    }

    @Test
    fun decodeRejectsWrongPartCount() {
        val bad = "t1.only-two-parts"
        assertThrows(TransferTokenFormatException::class.java) {
            TransferTokenCodec.decode(bad)
        }
    }
}
