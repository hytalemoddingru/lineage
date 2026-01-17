/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.config

import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64

/**
 * Result of backend config bootstrap.
 *
 * @property config Loaded configuration.
 * @property generatedSecret Non-null when a new config was created.
 */
data class BackendConfigBootstrap(
    val config: BackendConfig,
    val generatedSecret: String?,
)

/**
 * Loads and bootstraps backend-mod configuration.
 */
object BackendConfigLoader {
    private val secureRandom = SecureRandom()

    /**
     * Loads configuration from [path].
     */
    fun load(path: Path): BackendConfig {
        Files.newBufferedReader(path).use { reader ->
            return load(reader)
        }
    }

    /**
     * Loads configuration from a [reader].
     */
    fun load(reader: Reader): BackendConfig {
        val result = Toml.parse(reader)
        if (result.hasErrors()) {
            val errors = result.errors().joinToString("; ") { it.toString() }
            throw ConfigException("Failed to parse backend config: $errors")
        }
        return parseResult(result)
    }

    /**
     * Ensures configuration exists at [path]. If missing, creates a new config
     * using [defaultServerId] and a generated secret.
     */
    fun ensureConfig(path: Path, defaultServerId: String): BackendConfigBootstrap {
        require(defaultServerId.isNotBlank()) { "defaultServerId must not be blank" }
        if (Files.exists(path)) {
            return BackendConfigBootstrap(load(path), null)
        }
        val secret = generateSecret()
        val config = BackendConfig(
            schemaVersion = CURRENT_BACKEND_CONFIG_SCHEMA_VERSION,
            serverId = defaultServerId,
            proxySecret = secret,
            previousProxySecret = null,
            proxyConnectHost = DEFAULT_PROXY_CONNECT_HOST,
            proxyConnectPort = DEFAULT_PROXY_CONNECT_PORT,
            messagingHost = DEFAULT_MESSAGING_HOST,
            messagingPort = DEFAULT_MESSAGING_PORT,
            messagingEnabled = true,
            enforceProxy = true,
        )
        write(path, config)
        return BackendConfigBootstrap(config, secret)
    }

    private fun parseResult(result: TomlParseResult): BackendConfig {
        val schemaVersion = parseSchemaVersion(result)
        val serverId = requireString(result, "server_id")
        val proxySecret = requireString(result, "proxy_secret")
        val previousProxySecret = optionalString(result, "proxy_secret_previous")
        val proxyConnectHost = resolveProxyConnectHost(result)
        val proxyConnectPort = resolveProxyConnectPort(result)
        val messagingHost = resolveMessagingHost(result)
        val messagingPort = resolveMessagingPort(result)
        val messagingEnabled = result.getBoolean("messaging_enabled") ?: true
        val enforceProxy = result.getBoolean("enforce_proxy") ?: true
        return BackendConfig(
            schemaVersion = schemaVersion,
            serverId = serverId,
            proxySecret = proxySecret,
            previousProxySecret = previousProxySecret,
            proxyConnectHost = proxyConnectHost,
            proxyConnectPort = proxyConnectPort,
            messagingHost = messagingHost,
            messagingPort = messagingPort,
            messagingEnabled = messagingEnabled,
            enforceProxy = enforceProxy,
        )
    }

    private fun parseSchemaVersion(result: TomlParseResult): Int {
        val value = result.getLong("schema_version")
            ?: throw ConfigException("Missing schema_version")
        if (value <= 0 || value > Int.MAX_VALUE) {
            throw ConfigException("schema_version must be a positive integer")
        }
        val version = value.toInt()
        if (version != CURRENT_BACKEND_CONFIG_SCHEMA_VERSION) {
            throw ConfigException("Unsupported schema_version: $version")
        }
        return version
    }

    private fun requireString(result: TomlParseResult, key: String): String {
        val value = result.getString(key)
            ?: throw ConfigException("Missing $key")
        if (value.isBlank()) {
            throw ConfigException("$key must not be blank")
        }
        return value
    }

    private fun optionalString(result: TomlParseResult, key: String): String? {
        val value = result.getString(key) ?: return null
        if (value.isBlank()) {
            throw ConfigException("$key must not be blank")
        }
        return value
    }

    private fun write(path: Path, config: BackendConfig) {
        path.parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
            writer.appendLine("schema_version = ${config.schemaVersion}")
            writer.appendLine()
            writer.appendLine("server_id = \"${config.serverId}\"")
            writer.appendLine("proxy_secret = \"${config.proxySecret}\"")
            config.previousProxySecret?.let { previous ->
                writer.appendLine("proxy_secret_previous = \"$previous\"")
            }
            writer.appendLine("proxy_connect_host = \"${config.proxyConnectHost}\"")
            writer.appendLine("proxy_connect_port = ${config.proxyConnectPort}")
            writer.appendLine("messaging_host = \"${config.messagingHost}\"")
            writer.appendLine("messaging_port = ${config.messagingPort}")
            writer.appendLine("messaging_enabled = ${config.messagingEnabled}")
            writer.appendLine("enforce_proxy = ${config.enforceProxy}")
        }
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun resolveMessagingHost(result: TomlParseResult): String {
        val rawHost = result.getString("messaging_host")
            ?: result.getString("proxy_host")
        if (rawHost != null && rawHost.isBlank()) {
            throw ConfigException("messaging_host must not be blank")
        }
        return rawHost ?: DEFAULT_MESSAGING_HOST
    }

    private fun resolveMessagingPort(result: TomlParseResult): Int {
        val port = result.getLong("messaging_port")
            ?: result.getLong("proxy_port")
            ?: DEFAULT_MESSAGING_PORT.toLong()
        if (port !in 1..65535) {
            throw ConfigException("messaging_port must be between 1 and 65535")
        }
        return port.toInt()
    }

    private fun resolveProxyConnectHost(result: TomlParseResult): String {
        val rawHost = result.getString("proxy_connect_host")
        if (rawHost != null && rawHost.isBlank()) {
            throw ConfigException("proxy_connect_host must not be blank")
        }
        return rawHost ?: DEFAULT_PROXY_CONNECT_HOST
    }

    private fun resolveProxyConnectPort(result: TomlParseResult): Int {
        val port = result.getLong("proxy_connect_port")
            ?: DEFAULT_PROXY_CONNECT_PORT.toLong()
        if (port !in 1..65535) {
            throw ConfigException("proxy_connect_port must be between 1 and 65535")
        }
        return port.toInt()
    }

    private const val DEFAULT_PROXY_CONNECT_HOST = "127.0.0.1"
    private const val DEFAULT_PROXY_CONNECT_PORT = 25565
    private const val DEFAULT_MESSAGING_HOST = "127.0.0.1"
    private const val DEFAULT_MESSAGING_PORT = 25570
}
