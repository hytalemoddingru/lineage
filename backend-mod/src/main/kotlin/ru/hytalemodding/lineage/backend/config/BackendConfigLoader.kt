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
import ru.hytalemodding.lineage.shared.security.SecretStrengthPolicy
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
            controlSenderId = defaultServerId,
            controlMaxPayload = DEFAULT_CONTROL_MAX_PAYLOAD,
            controlReplayWindowMillis = DEFAULT_CONTROL_REPLAY_WINDOW_MILLIS,
            controlReplayMaxEntries = DEFAULT_CONTROL_REPLAY_MAX_ENTRIES,
            controlMaxSkewMillis = DEFAULT_CONTROL_MAX_SKEW_MILLIS,
            controlTtlMillis = DEFAULT_CONTROL_TTL_MILLIS,
            controlMaxInflight = DEFAULT_CONTROL_MAX_INFLIGHT,
            controlExpectedSenderId = DEFAULT_CONTROL_EXPECTED_SENDER_ID,
            requireAuthenticatedMode = true,
            enforceProxy = true,
            referralSourceHost = DEFAULT_PROXY_CONNECT_HOST,
            referralSourcePort = DEFAULT_PROXY_CONNECT_PORT,
            replayWindowMillis = DEFAULT_REPLAY_WINDOW_MILLIS,
            replayMaxEntries = DEFAULT_REPLAY_MAX_ENTRIES,
        )
        write(path, config)
        return BackendConfigBootstrap(config, secret)
    }

    private fun parseResult(result: TomlParseResult): BackendConfig {
        validateUnknownKeys(result)
        val schemaVersion = parseSchemaVersion(result)
        val serverId = requireString(result, "server_id")
        val proxySecret = requireString(result, "proxy_secret")
        SecretStrengthPolicy.validationError(proxySecret, "proxy_secret")?.let { message ->
            throw ConfigException(message)
        }
        val previousProxySecret = optionalString(result, "proxy_secret_previous")
        if (previousProxySecret != null) {
            SecretStrengthPolicy.validationError(previousProxySecret, "proxy_secret_previous")?.let { message ->
                throw ConfigException(message)
            }
        }
        val proxyConnectHost = resolveProxyConnectHost(result)
        val proxyConnectPort = resolveProxyConnectPort(result)
        val messagingHost = resolveMessagingHost(result)
        val messagingPort = resolveMessagingPort(result)
        val messagingEnabled = result.getBoolean("messaging_enabled") ?: true
        val controlSenderId = resolveControlSenderId(result, serverId)
        val controlMaxPayload = resolveControlMaxPayload(result)
        val controlReplayWindowMillis = resolveControlReplayWindow(result)
        val controlReplayMaxEntries = resolveControlReplayMaxEntries(result)
        val controlMaxSkewMillis = resolveControlMaxSkew(result)
        val controlTtlMillis = resolveControlTtl(result)
        val controlMaxInflight = resolveControlMaxInflight(result)
        val controlExpectedSenderId = resolveControlExpectedSenderId(result)
        val requireAuthenticatedMode = result.getBoolean("require_authenticated_mode") ?: true
        validateRemovedAgentFlags(result)
        val enforceProxy = result.getBoolean("enforce_proxy") ?: true
        if (!enforceProxy) {
            throw ConfigException("enforce_proxy must be true in v0.4.0")
        }
        validateEndpointConflicts(
            proxyConnectHost = proxyConnectHost,
            proxyConnectPort = proxyConnectPort,
            messagingHost = messagingHost,
            messagingPort = messagingPort,
            messagingEnabled = messagingEnabled,
        )
        val referralSourceHost = resolveReferralSourceHost(result, proxyConnectHost)
        val referralSourcePort = resolveReferralSourcePort(result, proxyConnectPort)
        val replayWindowMillis = resolveReplayWindow(result)
        val replayMaxEntries = resolveReplayMaxEntries(result)
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
            controlSenderId = controlSenderId,
            controlMaxPayload = controlMaxPayload,
            controlReplayWindowMillis = controlReplayWindowMillis,
            controlReplayMaxEntries = controlReplayMaxEntries,
            controlMaxSkewMillis = controlMaxSkewMillis,
            controlTtlMillis = controlTtlMillis,
            controlMaxInflight = controlMaxInflight,
            controlExpectedSenderId = controlExpectedSenderId,
            requireAuthenticatedMode = requireAuthenticatedMode,
            enforceProxy = enforceProxy,
            referralSourceHost = referralSourceHost,
            referralSourcePort = referralSourcePort,
            replayWindowMillis = replayWindowMillis,
            replayMaxEntries = replayMaxEntries,
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
            writer.write(
                buildString {
                    appendLine("# ============================================================")
                    appendLine("# Lineage Backend-Mod Configuration")
                    appendLine("# Audience: server administrators and operators.")
                    appendLine("# ============================================================")
                    appendLine()
                    appendLine("# Config schema version.")
                    appendLine("# TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.")
                    appendLine("schema_version = ${config.schemaVersion}")
                    appendLine()
                    appendLine("# --- Backend identity ---")
                    appendLine("# Unique backend id used by proxy routing and transfer commands.")
                    appendLine("server_id = \"${config.serverId}\"")
                    appendLine()
                    appendLine("# --- Trust boundary with proxy ---")
                    appendLine("# Shared HMAC secret used to validate control/referral traffic.")
                    appendLine("# MUST match proxy security.proxy_secret.")
                    appendLine("# TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.")
                    appendLine("proxy_secret = \"${config.proxySecret}\"")
                    config.previousProxySecret?.let { previous ->
                        appendLine("# Previous proxy secret accepted during secret rotation window.")
                        appendLine("# Optional; remove after rollout is complete.")
                        appendLine("proxy_secret_previous = \"$previous\"")
                    }
                    appendLine()
                    appendLine("# --- How this backend connects players back to proxy ---")
                    appendLine("# Proxy host sent to backend referral system.")
                    appendLine("proxy_connect_host = \"${config.proxyConnectHost}\"")
                    appendLine("# Proxy port players should reconnect through.")
                    appendLine("proxy_connect_port = ${config.proxyConnectPort}")
                    appendLine()
                    appendLine("# --- Referral source metadata embedded into redirects ---")
                    appendLine("# Usually same as proxy_connect_host/proxy_connect_port.")
                    appendLine("referral_source_host = \"${config.referralSourceHost}\"")
                    appendLine("referral_source_port = ${config.referralSourcePort}")
                    appendLine()
                    appendLine("# --- UDP messaging channel to proxy ---")
                    appendLine("# TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.")
                    appendLine("# UDP destination host where proxy messaging listens.")
                    appendLine("messaging_host = \"${config.messagingHost}\"")
                    appendLine("# UDP destination port where proxy messaging listens.")
                    appendLine("messaging_port = ${config.messagingPort}")
                    appendLine("# Master switch for backend<->proxy control-plane.")
                    appendLine("messaging_enabled = ${config.messagingEnabled}")
                    appendLine()
                    appendLine("# --- Control-plane envelope policy ---")
                    appendLine("# Sender id stamped into backend control messages.")
                    appendLine("control_sender_id = \"${config.controlSenderId}\"")
                    appendLine("# Max payload bytes accepted by backend control-plane.")
                    appendLine("control_max_payload = ${config.controlMaxPayload}")
                    appendLine("# Replay protection window in milliseconds.")
                    appendLine("control_replay_window_millis = ${config.controlReplayWindowMillis}")
                    appendLine("# Replay cache size for control-plane messages.")
                    appendLine("control_replay_max_entries = ${config.controlReplayMaxEntries}")
                    appendLine("# Allowed clock skew in milliseconds.")
                    appendLine("control_max_skew_millis = ${config.controlMaxSkewMillis}")
                    appendLine("# Envelope TTL in milliseconds.")
                    appendLine("control_ttl_millis = ${config.controlTtlMillis}")
                    appendLine("# Max concurrently processed inbound control messages.")
                    appendLine("control_max_inflight = ${config.controlMaxInflight}")
                    appendLine("# Expected sender id for inbound proxy control messages.")
                    appendLine("control_expected_sender_id = \"${config.controlExpectedSenderId}\"")
                    appendLine()
                    appendLine("# --- Security invariants ---")
                    appendLine("# Keep true unless you are intentionally running insecure diagnostics.")
                    appendLine("# TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.")
                    appendLine("# Require AUTHENTICATED mode on Hytale server.")
                    appendLine("require_authenticated_mode = ${config.requireAuthenticatedMode}")
                    appendLine("# Enforce proxy-only entrypoint (direct join must be blocked).")
                    appendLine("enforce_proxy = ${config.enforceProxy}")
                    appendLine()
                    appendLine("# --- Replay protection for referral tokens ---")
                    appendLine("# Referral replay window in milliseconds.")
                    appendLine("replay_window_millis = ${config.replayWindowMillis}")
                    appendLine("# Max referral replay entries kept in memory.")
                    appendLine("replay_max_entries = ${config.replayMaxEntries}")
                }
            )
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

    private fun resolveReferralSourceHost(result: TomlParseResult, proxyConnectHost: String): String {
        val rawHost = result.getString("referral_source_host") ?: proxyConnectHost
        if (rawHost.isBlank()) {
            throw ConfigException("referral_source_host must not be blank")
        }
        return rawHost
    }

    private fun resolveReferralSourcePort(result: TomlParseResult, proxyConnectPort: Int): Int {
        val port = result.getLong("referral_source_port") ?: proxyConnectPort.toLong()
        if (port !in 1..65535) {
            throw ConfigException("referral_source_port must be between 1 and 65535")
        }
        return port.toInt()
    }

    private fun resolveReplayWindow(result: TomlParseResult): Long {
        val window = result.getLong("replay_window_millis") ?: DEFAULT_REPLAY_WINDOW_MILLIS
        if (window <= 0) {
            throw ConfigException("replay_window_millis must be > 0")
        }
        return window
    }

    private fun resolveReplayMaxEntries(result: TomlParseResult): Int {
        val value = result.getLong("replay_max_entries") ?: DEFAULT_REPLAY_MAX_ENTRIES.toLong()
        if (value <= 0 || value > Int.MAX_VALUE) {
            throw ConfigException("replay_max_entries must be a positive integer")
        }
        return value.toInt()
    }

    private fun resolveControlSenderId(result: TomlParseResult, serverId: String): String {
        val value = result.getString("control_sender_id") ?: serverId
        if (value.isBlank()) {
            throw ConfigException("control_sender_id must not be blank")
        }
        return value
    }

    private fun resolveControlMaxPayload(result: TomlParseResult): Int {
        val value = result.getLong("control_max_payload") ?: DEFAULT_CONTROL_MAX_PAYLOAD.toLong()
        if (value !in 1..MAX_CONTROL_PAYLOAD_BYTES) {
            throw ConfigException("control_max_payload must be in 1..$MAX_CONTROL_PAYLOAD_BYTES")
        }
        return value.toInt()
    }

    private fun resolveControlReplayWindow(result: TomlParseResult): Long {
        val value = result.getLong("control_replay_window_millis") ?: DEFAULT_CONTROL_REPLAY_WINDOW_MILLIS
        if (value <= 0) {
            throw ConfigException("control_replay_window_millis must be > 0")
        }
        return value
    }

    private fun resolveControlReplayMaxEntries(result: TomlParseResult): Int {
        val value = result.getLong("control_replay_max_entries") ?: DEFAULT_CONTROL_REPLAY_MAX_ENTRIES.toLong()
        if (value <= 0 || value > Int.MAX_VALUE) {
            throw ConfigException("control_replay_max_entries must be a positive integer")
        }
        return value.toInt()
    }

    private fun resolveControlMaxSkew(result: TomlParseResult): Long {
        val value = result.getLong("control_max_skew_millis") ?: DEFAULT_CONTROL_MAX_SKEW_MILLIS
        if (value < 0) {
            throw ConfigException("control_max_skew_millis must be >= 0")
        }
        return value
    }

    private fun resolveControlTtl(result: TomlParseResult): Long {
        val value = result.getLong("control_ttl_millis") ?: DEFAULT_CONTROL_TTL_MILLIS
        if (value <= 0) {
            throw ConfigException("control_ttl_millis must be > 0")
        }
        return value
    }

    private fun resolveControlMaxInflight(result: TomlParseResult): Int {
        val value = result.getLong("control_max_inflight") ?: DEFAULT_CONTROL_MAX_INFLIGHT.toLong()
        if (value <= 0 || value > Int.MAX_VALUE) {
            throw ConfigException("control_max_inflight must be a positive integer")
        }
        return value.toInt()
    }

    private fun resolveControlExpectedSenderId(result: TomlParseResult): String {
        val value = result.getString("control_expected_sender_id") ?: DEFAULT_CONTROL_EXPECTED_SENDER_ID
        if (value.isBlank()) {
            throw ConfigException("control_expected_sender_id must not be blank")
        }
        return value
    }

    private fun validateRemovedAgentFlags(result: TomlParseResult) {
        if (result.contains("agentless")) {
            throw ConfigException("agentless is removed in v0.4.0; remove this key from config")
        }
        if (result.contains("javaagent_fallback")) {
            throw ConfigException("javaagent_fallback is removed in v0.4.0; JavaAgent mode is no longer supported")
        }
    }

    private fun validateUnknownKeys(result: TomlParseResult) {
        val unknown = result.keySet()
            .filterNot { it in ALLOWED_KEYS }
            .sorted()
        if (unknown.isNotEmpty()) {
            throw ConfigException("Unknown backend config keys: ${unknown.joinToString(", ")}")
        }
    }

    private fun validateEndpointConflicts(
        proxyConnectHost: String,
        proxyConnectPort: Int,
        messagingHost: String,
        messagingPort: Int,
        messagingEnabled: Boolean,
    ) {
        if (!messagingEnabled) {
            return
        }
        if (proxyConnectPort == messagingPort && hostBindingsOverlap(proxyConnectHost, messagingHost)) {
            throw ConfigException(
                "proxy_connect_host/proxy_connect_port cannot overlap messaging_host/messaging_port when messaging is enabled"
            )
        }
    }

    private fun hostBindingsOverlap(left: String, right: String): Boolean {
        val leftHost = left.trim().lowercase()
        val rightHost = right.trim().lowercase()
        if (leftHost == rightHost) {
            return true
        }
        return leftHost in WILDCARD_BIND_HOSTS || rightHost in WILDCARD_BIND_HOSTS
    }

    private const val DEFAULT_PROXY_CONNECT_HOST = "127.0.0.1"
    private const val DEFAULT_PROXY_CONNECT_PORT = 25565
    private const val DEFAULT_MESSAGING_HOST = "127.0.0.1"
    private const val DEFAULT_MESSAGING_PORT = 25570
    private const val DEFAULT_REPLAY_WINDOW_MILLIS = 10_000L
    private const val DEFAULT_REPLAY_MAX_ENTRIES = 100_000
    private const val DEFAULT_CONTROL_MAX_PAYLOAD = 8192
    private const val DEFAULT_CONTROL_REPLAY_WINDOW_MILLIS = 10_000L
    private const val DEFAULT_CONTROL_REPLAY_MAX_ENTRIES = 100_000
    private const val DEFAULT_CONTROL_MAX_SKEW_MILLIS = 120_000L
    private const val DEFAULT_CONTROL_TTL_MILLIS = 10_000L
    private const val DEFAULT_CONTROL_MAX_INFLIGHT = 256
    private const val DEFAULT_CONTROL_EXPECTED_SENDER_ID = "proxy"
    private const val MAX_CONTROL_PAYLOAD_BYTES = 65_535
    private val WILDCARD_BIND_HOSTS = setOf("0.0.0.0", "::", "[::]")
    private val ALLOWED_KEYS = setOf(
        "schema_version",
        "server_id",
        "proxy_secret",
        "proxy_secret_previous",
        "proxy_connect_host",
        "proxy_connect_port",
        "proxy_host",
        "proxy_port",
        "messaging_host",
        "messaging_port",
        "messaging_enabled",
        "control_sender_id",
        "control_max_payload",
        "control_replay_window_millis",
        "control_replay_max_entries",
        "control_max_skew_millis",
        "control_ttl_millis",
        "control_max_inflight",
        "control_expected_sender_id",
        "require_authenticated_mode",
        "enforce_proxy",
        "referral_source_host",
        "referral_source_port",
        "replay_window_millis",
        "replay_max_entries",
        "agentless",
        "javaagent_fallback",
    )
}
