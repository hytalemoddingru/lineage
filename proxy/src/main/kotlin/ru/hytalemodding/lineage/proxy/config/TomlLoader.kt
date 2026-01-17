/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.config

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path

import java.nio.charset.StandardCharsets

/**
 * Loads and validates `config.toml` for the proxy.
 */
object TomlLoader {
    /**
     * Ensures configuration exists at [path]. If missing, creates a default config.
     */
    fun ensureConfig(path: Path) {
        if (Files.exists(path)) return

        path.parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
            writer.write("""
                schema_version = 1

                [listener]
                host = "0.0.0.0"
                port = 25565

                [security]
                # REPLACE THIS WITH A SECURE RANDOM STRING!
                # The backend-mod will generate one for you on first run.
                proxy_secret = "change-me-please"
                token_ttl_millis = 30000

                [[backends]]
                id = "server-1"
                host = "127.0.0.1"
                port = 30000

                [routing]
                default_backend_id = "server-1"

                [messaging]
                enabled = false
                host = "0.0.0.0"
                port = 25570
            """.trimIndent())
        }
    }

    /**
     * Loads configuration from a file [path].
     */
    fun load(path: Path): ProxyConfig {
        ensureConfig(path)
        Files.newBufferedReader(path).use { reader ->
            return load(reader)
        }
    }

    /**
     * Loads configuration from a [reader].
     */
    fun load(reader: Reader): ProxyConfig {
        val result = Toml.parse(reader)
        if (result.hasErrors()) {
            val errors = result.errors().joinToString("; ") { it.toString() }
            throw ConfigException("Failed to parse config.toml: $errors")
        }
        return parseResult(result)
    }

    private fun parseResult(result: TomlParseResult): ProxyConfig {
        val schemaVersion = parseSchemaVersion(result)
        val listener = parseListener(result.getTable("listener"))
        val security = parseSecurity(result.getTable("security"))
        val backends = parseBackends(result.getArray("backends"))
        val routing = parseRouting(result.getTable("routing"))
        val messaging = parseMessaging(result.getTable("messaging"))

        val backendIds = backends.map { it.id }.toSet()
        if (routing.defaultBackendId !in backendIds) {
            throw ConfigException("routing.default_backend_id must reference an existing backend id")
        }

        return ProxyConfig(
            schemaVersion = schemaVersion,
            listener = listener,
            security = security,
            backends = backends,
            routing = routing,
            messaging = messaging,
        )
    }

    private fun parseSchemaVersion(result: TomlParseResult): Int {
        val value = result.getLong("schema_version")
            ?: throw ConfigException("Missing schema_version")
        if (value <= 0 || value > Int.MAX_VALUE) {
            throw ConfigException("schema_version must be a positive integer")
        }
        val version = value.toInt()
        if (version != CURRENT_CONFIG_SCHEMA_VERSION) {
            throw ConfigException("Unsupported schema_version: $version")
        }
        return version
    }

    private fun parseListener(table: TomlTable?): ListenerConfig {
        val listener = table ?: throw ConfigException("Missing [listener] section")
        val host = requireString(listener, "host", "listener.host")
        val port = requirePort(listener, "port", "listener.port")
        return ListenerConfig(host = host, port = port)
    }

    private fun parseSecurity(table: TomlTable?): SecurityConfig {
        val security = table ?: throw ConfigException("Missing [security] section")
        val proxySecret = requireString(security, "proxy_secret", "security.proxy_secret")
        val tokenTtl = requirePositiveLong(security, "token_ttl_millis", "security.token_ttl_millis")
        return SecurityConfig(proxySecret = proxySecret, tokenTtlMillis = tokenTtl)
    }

    private fun parseBackends(array: TomlArray?): List<BackendConfig> {
        val backendsArray = array ?: throw ConfigException("Missing [[backends]] array")
        if (backendsArray.isEmpty()) {
            throw ConfigException("At least one backend must be configured")
        }
        val seenIds = mutableSetOf<String>()
        val backends = mutableListOf<BackendConfig>()
        for (i in 0 until backendsArray.size()) {
            val backend = backendsArray.getTable(i)
                ?: throw ConfigException("backends[$i] must be a table")
            val id = requireString(backend, "id", "backends[$i].id")
            if (!seenIds.add(id)) {
                throw ConfigException("Duplicate backend id: $id")
            }
            val host = requireString(backend, "host", "backends[$i].host")
            val port = requirePort(backend, "port", "backends[$i].port")
            backends.add(BackendConfig(id = id, host = host, port = port))
        }
        return backends
    }

    private fun parseRouting(table: TomlTable?): RoutingConfig {
        val routing = table ?: throw ConfigException("Missing [routing] section")
        val defaultBackendId = requireString(routing, "default_backend_id", "routing.default_backend_id")
        return RoutingConfig(defaultBackendId = defaultBackendId)
    }

    private fun parseMessaging(table: TomlTable?): MessagingConfig {
        val defaults = MessagingConfig(host = "0.0.0.0", port = 25570, enabled = false)
        if (table == null) {
            return defaults
        }
        val enabled = table.getBoolean("enabled") ?: defaults.enabled
        val host = table.getString("host") ?: defaults.host
        val port = table.getLong("port")?.toInt() ?: defaults.port
        if (host.isBlank()) {
            throw ConfigException("messaging.host must not be blank")
        }
        if (port !in 1..65535) {
            throw ConfigException("messaging.port must be between 1 and 65535")
        }
        return MessagingConfig(host = host, port = port, enabled = enabled)
    }

    private fun requireString(table: TomlTable, key: String, path: String): String {
        val value = table.getString(key)
            ?: throw ConfigException("Missing $path")
        if (value.isBlank()) {
            throw ConfigException("$path must not be blank")
        }
        return value
    }

    private fun requirePositiveLong(table: TomlTable, key: String, path: String): Long {
        val value = table.getLong(key)
            ?: throw ConfigException("Missing $path")
        if (value <= 0) {
            throw ConfigException("$path must be > 0")
        }
        return value
    }

    private fun requirePort(table: TomlTable, key: String, path: String): Int {
        val value = table.getLong(key)
            ?: throw ConfigException("Missing $path")
        if (value !in 1..65535) {
            throw ConfigException("$path must be between 1 and 65535")
        }
        return value.toInt()
    }
}
