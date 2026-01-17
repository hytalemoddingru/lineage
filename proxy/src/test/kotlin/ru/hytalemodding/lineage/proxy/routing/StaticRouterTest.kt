/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.routing

import ru.hytalemodding.lineage.proxy.config.BackendConfig
import ru.hytalemodding.lineage.proxy.config.CURRENT_CONFIG_SCHEMA_VERSION
import ru.hytalemodding.lineage.proxy.config.ListenerConfig
import ru.hytalemodding.lineage.proxy.config.MessagingConfig
import ru.hytalemodding.lineage.proxy.config.ProxyConfig
import ru.hytalemodding.lineage.proxy.config.RoutingConfig
import ru.hytalemodding.lineage.proxy.config.SecurityConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StaticRouterTest {
    @Test
    fun returnsDefaultBackend() {
        val router = StaticRouter(sampleConfig())
        val backend = router.selectInitialBackend()

        assertEquals("hub", backend.id)
    }

    @Test
    fun findsBackendById() {
        val router = StaticRouter(sampleConfig())

        assertEquals("minigame", router.findBackend("minigame")?.id)
        assertNull(router.findBackend("missing"))
    }

    @Test
    fun throwsWhenDefaultMissing() {
        val config = sampleConfig().copy(routing = RoutingConfig(defaultBackendId = "missing"))
        val router = StaticRouter(config)

        assertThrows(IllegalStateException::class.java) {
            router.selectInitialBackend()
        }
    }

    private fun sampleConfig(): ProxyConfig {
        return ProxyConfig(
            schemaVersion = CURRENT_CONFIG_SCHEMA_VERSION,
            listener = ListenerConfig(host = "0.0.0.0", port = 25565),
            security = SecurityConfig(proxySecret = "secret", tokenTtlMillis = 30_000),
            backends = listOf(
                BackendConfig(id = "hub", host = "127.0.0.1", port = 25566),
                BackendConfig(id = "minigame", host = "127.0.0.1", port = 25567),
            ),
            routing = RoutingConfig(defaultBackendId = "hub"),
            messaging = MessagingConfig(host = "0.0.0.0", port = 25570, enabled = false),
        )
    }
}
