/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy

import ru.hytalemodding.lineage.proxy.config.TomlLoader
import ru.hytalemodding.lineage.api.routing.RoutingContext
import ru.hytalemodding.lineage.api.routing.RoutingStrategy
import ru.hytalemodding.lineage.proxy.routing.StaticRoutingStrategy
import ru.hytalemodding.lineage.proxy.routing.StrategyRouter
import ru.hytalemodding.lineage.proxy.security.TokenService
import ru.hytalemodding.lineage.proxy.service.ServiceRegistryImpl
import ru.hytalemodding.lineage.shared.time.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.nio.charset.StandardCharsets

class ProxySmokeTest {
    @Test
    fun createsCoreComponents() {
        val toml = """
            schema_version = 1

            [listener]
            host = "0.0.0.0"
            port = 25565

            [security]
            proxy_secret = "secret-123"
            token_ttl_millis = 30000

            [[backends]]
            id = "hub"
            host = "127.0.0.1"
            port = 30000

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        val config = TomlLoader.load(StringReader(toml))
        val services = ServiceRegistryImpl()
        val strategy = StaticRoutingStrategy(config)
        services.register(RoutingStrategy.SERVICE_KEY, strategy)
        val router = StrategyRouter(config, services, strategy)
        val backend = router.selectInitialBackend(
            RoutingContext(null, null, null, null, null)
        )
        val tokenService = TokenService(
            secret = config.security.proxySecret.toByteArray(StandardCharsets.UTF_8),
            tokenTtlMillis = config.security.tokenTtlMillis,
            clock = FixedClock(1_000L),
        )

        val token = tokenService.issueToken("player-1", backend.id)

        assertEquals("hub", backend.id)
        assertTrue(token.isNotBlank())
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
