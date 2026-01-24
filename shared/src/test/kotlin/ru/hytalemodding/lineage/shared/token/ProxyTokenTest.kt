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

class ProxyTokenTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val token = ProxyToken(
            version = CURRENT_PROXY_TOKEN_VERSION,
            playerId = "player-123",
            targetServerId = "hub-1",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
            nonceB64 = "nonce-value",
            clientCertB64 = "client-cert",
            proxyCertB64 = "proxy-cert",
        )
        val secret = "proxy-secret".toByteArray()

        val encoded = ProxyTokenCodec.encode(token, secret)
        val parsed = ProxyTokenCodec.decode(encoded)

        assertEquals(token, parsed.token)
        assertTrue(ProxyTokenCodec.verifySignature(parsed, secret))
        assertFalse(ProxyTokenCodec.verifySignature(parsed, "wrong-secret".toByteArray()))
    }

    @Test
    fun encodeDecodeRoundTripLegacyVersion() {
        val token = ProxyToken(
            version = LEGACY_PROXY_TOKEN_VERSION,
            playerId = "player-legacy",
            targetServerId = "hub-legacy",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
            clientCertB64 = "legacy-cert",
        )
        val secret = "proxy-secret".toByteArray()

        val encoded = ProxyTokenCodec.encode(token, secret)
        val parsed = ProxyTokenCodec.decode(encoded)

        assertEquals(token, parsed.token)
        assertTrue(ProxyTokenCodec.verifySignature(parsed, secret))
    }

    @Test
    fun decodeRejectsBadPrefix() {
        val bad = "v2.payload.signature"
        assertThrows(ProxyTokenFormatException::class.java) {
            ProxyTokenCodec.decode(bad)
        }
    }

    @Test
    fun decodeRejectsWrongPartCount() {
        val bad = "v1.only-two-parts"
        assertThrows(ProxyTokenFormatException::class.java) {
            ProxyTokenCodec.decode(bad)
        }
    }
}
