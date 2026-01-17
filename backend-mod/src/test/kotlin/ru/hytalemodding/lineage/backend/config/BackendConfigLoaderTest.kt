/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.StringReader

class BackendConfigLoaderTest {
    @Test
    fun loadsValidConfig() {
        val toml = """
            schema_version = 1
            server_id = "hub"
            proxy_secret = "secret-123"
        """.trimIndent()

        val config = BackendConfigLoader.load(StringReader(toml))

        assertEquals("hub", config.serverId)
        assertEquals("secret-123", config.proxySecret)
        assertEquals(null, config.previousProxySecret)
        assertEquals("127.0.0.1", config.proxyConnectHost)
        assertEquals(25565, config.proxyConnectPort)
        assertEquals("127.0.0.1", config.messagingHost)
        assertEquals(25570, config.messagingPort)
        assertEquals(true, config.messagingEnabled)
        assertEquals(true, config.enforceProxy)
    }

    @Test
    fun loadsPreviousSecretWhenPresent() {
        val toml = """
            schema_version = 1
            server_id = "hub"
            proxy_secret = "secret-123"
            proxy_secret_previous = "old-secret"
        """.trimIndent()

        val config = BackendConfigLoader.load(StringReader(toml))

        assertEquals("secret-123", config.proxySecret)
        assertEquals("old-secret", config.previousProxySecret)
        assertEquals("127.0.0.1", config.proxyConnectHost)
        assertEquals(25565, config.proxyConnectPort)
        assertEquals("127.0.0.1", config.messagingHost)
        assertEquals(25570, config.messagingPort)
        assertEquals(true, config.messagingEnabled)
        assertEquals(true, config.enforceProxy)
    }

    @Test
    fun loadsLegacyProxyHostKeys() {
        val toml = """
            schema_version = 1
            server_id = "hub"
            proxy_secret = "secret-123"
            proxy_connect_host = "192.168.1.10"
            proxy_connect_port = 25566
            proxy_host = "10.0.0.5"
            proxy_port = 25571
            enforce_proxy = false
        """.trimIndent()

        val config = BackendConfigLoader.load(StringReader(toml))

        assertEquals("192.168.1.10", config.proxyConnectHost)
        assertEquals(25566, config.proxyConnectPort)
        assertEquals("10.0.0.5", config.messagingHost)
        assertEquals(25571, config.messagingPort)
        assertEquals(false, config.enforceProxy)
    }

    @Test
    fun rejectsMissingSchemaVersion() {
        val toml = """
            server_id = "hub"
            proxy_secret = "secret-123"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            BackendConfigLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsBlankServerId() {
        val toml = """
            schema_version = 1
            server_id = ""
            proxy_secret = "secret-123"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            BackendConfigLoader.load(StringReader(toml))
        }
    }
}
