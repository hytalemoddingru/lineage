/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.StringReader

class TomlLoaderTest {
    @Test
    fun loadsValidConfig() {
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
            port = 25566

            [[backends]]
            id = "minigame"
            host = "127.0.0.1"
            port = 25567

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        val config = TomlLoader.load(StringReader(toml))

        assertEquals("0.0.0.0", config.listener.host)
        assertEquals(25565, config.listener.port)
        assertEquals("secret-123", config.security.proxySecret)
        assertEquals(30000, config.security.tokenTtlMillis)
        assertEquals("hub", config.routing.defaultBackendId)
        assertEquals(2, config.backends.size)
    }

    @Test
    fun rejectsMissingListener() {
        val toml = """
            schema_version = 1

            [security]
            proxy_secret = "secret-123"
            token_ttl_millis = 30000

            [[backends]]
            id = "hub"
            host = "127.0.0.1"
            port = 25566

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsDuplicateBackendIds() {
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
            port = 25566

            [[backends]]
            id = "hub"
            host = "127.0.0.1"
            port = 25567

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsUnknownDefaultBackend() {
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
            port = 25566

            [routing]
            default_backend_id = "unknown"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val toml = """
            schema_version = 2

            [listener]
            host = "0.0.0.0"
            port = 25565

            [security]
            proxy_secret = "secret-123"
            token_ttl_millis = 30000

            [[backends]]
            id = "hub"
            host = "127.0.0.1"
            port = 25566

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }
}
