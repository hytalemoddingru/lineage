/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.token.CURRENT_PROXY_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.ProxyTokenCodec

class TokenServiceTest {
    private val secret = "secret-123".toByteArray()

    @Test
    fun issuesCurrentVersionTokenWithCertificateClaims() {
        val service = TokenService(secret, tokenTtlMillis = 30_000, clock = FixedClock(1_000))

        val encoded = service.issueToken(
            playerId = "player-1",
            targetServerId = "hub",
            clientCertB64 = "client-cert",
            proxyCertB64 = "proxy-cert",
        )
        val parsed = ProxyTokenCodec.decode(encoded).token

        assertEquals(CURRENT_PROXY_TOKEN_VERSION, parsed.version)
        assertEquals("client-cert", parsed.clientCertB64)
        assertEquals("proxy-cert", parsed.proxyCertB64)
    }

    @Test
    fun rejectsMissingClientCertificate() {
        val service = TokenService(secret, tokenTtlMillis = 30_000, clock = FixedClock(1_000))

        assertThrows(IllegalArgumentException::class.java) {
            service.issueToken(
                playerId = "player-1",
                targetServerId = "hub",
                clientCertB64 = null,
                proxyCertB64 = "proxy-cert",
            )
        }
    }

    @Test
    fun rejectsMissingProxyCertificate() {
        val service = TokenService(secret, tokenTtlMillis = 30_000, clock = FixedClock(1_000))

        assertThrows(IllegalArgumentException::class.java) {
            service.issueToken(
                playerId = "player-1",
                targetServerId = "hub",
                clientCertB64 = "client-cert",
                proxyCertB64 = "",
            )
        }
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
