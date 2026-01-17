/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend

import ru.hytalemodding.lineage.backend.config.BackendConfigLoader
import ru.hytalemodding.lineage.backend.handshake.HandshakeInterceptor
import ru.hytalemodding.lineage.backend.security.TokenValidator
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.ProxyTokenCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.nio.charset.StandardCharsets

class BackendSmokeTest {
    @Test
    fun validatesReferralTokenWithConfig() {
        val toml = """
            schema_version = 1
            server_id = "hub"
            proxy_secret = "secret-123"
        """.trimIndent()
        val config = BackendConfigLoader.load(StringReader(toml))
        val clock = FixedClock(1_500L)
        val validator = TokenValidator(config.proxySecret.toByteArray(StandardCharsets.UTF_8), clock)
        val interceptor = HandshakeInterceptor(validator, config.serverId)
        val token = ProxyToken(
            version = 1,
            playerId = "player-1",
            targetServerId = "hub",
            issuedAtMillis = 1_000L,
            expiresAtMillis = 2_000L,
        )
        val encoded = ProxyTokenCodec.encode(token, config.proxySecret.toByteArray(StandardCharsets.UTF_8))

        val parsed = interceptor.validateReferralData(encoded.toByteArray(StandardCharsets.UTF_8))

        assertEquals("player-1", parsed.playerId)
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
