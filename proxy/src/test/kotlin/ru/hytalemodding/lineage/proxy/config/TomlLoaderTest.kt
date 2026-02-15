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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [[backends]]
            id = "minigame"
            host = "127.0.0.1"
            port = 25567
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

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
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", config.backends.first().certFingerprintSha256)
        assertEquals("0.0.0.0", config.referral.host)
        assertEquals(25565, config.referral.port)
        assertEquals(256, config.messaging.controlMaxInflight)
        assertEquals(false, config.observability.enabled)
        assertEquals("127.0.0.1", config.observability.host)
        assertEquals(9100, config.observability.port)
        assertEquals(false, config.logging.debug)
        assertEquals(10, config.logging.maxArchiveFiles)
        assertEquals(50, config.console.historyLimit)
        assertEquals(256, config.rateLimits.handshakeConcurrentMax)
        assertEquals(256, config.rateLimits.routingConcurrentMax)
        assertEquals(20, config.rateLimits.connectionPerIp.maxEvents)
        assertEquals(10_000L, config.rateLimits.connectionPerIp.windowMillis)
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [[backends]]
            id = "hub"
            host = "127.0.0.1"
            port = 25567
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsWeakDefaultProxySecret() {
        val toml = """
            schema_version = 1

            [listener]
            host = "0.0.0.0"
            port = 25565

            [security]
            proxy_secret = "change-me-please"
            token_ttl_millis = 30000

            [[backends]]
            id = "hub"
            host = "127.0.0.1"
            port = 25566
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsBackendWithoutCertFingerprintPinInStrictMode() {
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
            cert_trust_mode = "strict"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun allowsBackendWithoutFingerprintInTofuMode() {
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
            cert_trust_mode = "tofu"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        val config = TomlLoader.load(StringReader(toml))

        assertEquals(BackendCertTrustMode.TOFU, config.backends.first().certTrustMode)
        assertEquals(null, config.backends.first().certFingerprintSha256)
    }

    @Test
    fun rejectsUnknownBackendTrustMode() {
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
            cert_trust_mode = "invalid-mode"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsListenerAndMessagingEndpointConflict() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [messaging]
            enabled = true
            host = "127.0.0.1"
            port = 25565
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsDuplicateBackendEndpoint() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [[backends]]
            id = "pvp"
            host = "127.0.0.1"
            port = 25566
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsWildcardBackendHost() {
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
            host = "0.0.0.0"
            port = 25566
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsListenerAndBackendEndpointConflict() {
        val toml = """
            schema_version = 1

            [listener]
            host = "127.0.0.1"
            port = 25565

            [security]
            proxy_secret = "secret-123"
            token_ttl_millis = 30000

            [[backends]]
            id = "hub"
            host = "127.0.0.1"
            port = 25565
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsTooLargeControlMaxPayload() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [messaging]
            enabled = true
            host = "127.0.0.1"
            port = 25570
            control_max_payload = 70000
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsNonPositiveControlMaxInflight() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [messaging]
            enabled = true
            host = "127.0.0.1"
            port = 25570
            control_max_inflight = 0
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsListenerAndObservabilityEndpointConflict() {
        val toml = """
            schema_version = 1

            [listener]
            host = "127.0.0.1"
            port = 25565

            [security]
            proxy_secret = "secret-123"
            token_ttl_millis = 30000

            [[backends]]
            id = "hub"
            host = "127.0.0.1"
            port = 25566
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [observability]
            enabled = true
            host = "127.0.0.1"
            port = 25565
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsInvalidObservabilityPort() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [observability]
            enabled = true
            host = "127.0.0.1"
            port = 70000
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsNonPositiveHandshakeConcurrentMax() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [rate_limits]
            handshake_concurrent_max = 0
            connection_per_ip_max = 20
            connection_per_ip_window_millis = 10000
            handshake_per_ip_max = 20
            handshake_per_ip_window_millis = 10000
            streams_per_session_max = 8
            streams_per_session_window_millis = 10000
            invalid_packets_per_session_max = 4
            invalid_packets_per_session_window_millis = 10000
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsNonPositiveRoutingConcurrentMax() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [rate_limits]
            handshake_concurrent_max = 256
            routing_concurrent_max = 0
            connection_per_ip_max = 20
            connection_per_ip_window_millis = 10000
            handshake_per_ip_max = 20
            handshake_per_ip_window_millis = 10000
            streams_per_session_max = 8
            streams_per_session_window_millis = 10000
            invalid_packets_per_session_max = 4
            invalid_packets_per_session_window_millis = 10000
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsUnknownTopLevelConfigKey() {
        val toml = """
            schema_version = 1
            unknown_root_key = "value"

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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsUnknownMessagingConfigKey() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [messaging]
            enabled = true
            host = "127.0.0.1"
            port = 25570
            unknown_messaging_key = true
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun parsesLoggingDebugFlag() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [logging]
            debug = true
            max_archive_files = 25
        """.trimIndent()

        val config = TomlLoader.load(StringReader(toml))
        assertEquals(true, config.logging.debug)
        assertEquals(25, config.logging.maxArchiveFiles)
    }

    @Test
    fun parsesConsoleHistoryLimit() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [console]
            history_limit = 120
        """.trimIndent()

        val config = TomlLoader.load(StringReader(toml))
        assertEquals(120, config.console.historyLimit)
    }

    @Test
    fun rejectsNonPositiveConsoleHistoryLimit() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [console]
            history_limit = 0
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }

    @Test
    fun rejectsNonPositiveLoggingMaxArchiveFiles() {
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
            cert_fingerprint_sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

            [routing]
            default_backend_id = "hub"

            [logging]
            max_archive_files = 0
        """.trimIndent()

        assertThrows(ConfigException::class.java) {
            TomlLoader.load(StringReader(toml))
        }
    }
}
