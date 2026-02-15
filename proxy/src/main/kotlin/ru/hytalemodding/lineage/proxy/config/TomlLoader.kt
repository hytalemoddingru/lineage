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
import ru.hytalemodding.lineage.shared.protocol.ProtocolLimitsConfig
import ru.hytalemodding.lineage.shared.security.CertificateFingerprint
import ru.hytalemodding.lineage.shared.security.SecretStrengthPolicy
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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
                # ============================================================
                # Lineage Proxy Configuration
                # Audience: server administrators and operators.
                # ============================================================

                # Config schema version.
                # TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.
                schema_version = 1

                # --- Client listener (players connect here) ---
                # Bind address for incoming player connections.
                [listener]
                # Host/IP to bind on.
                host = "0.0.0.0"
                # UDP/QUIC port exposed to players.
                port = 25565

                # --- Security (shared secret with backend-mod) ---
                [security]
                # Shared HMAC secret used for proxy/backend trust.
                # MUST match backend-mod proxy_secret.
                # REPLACE THIS WITH A STRONG RANDOM VALUE.
                proxy_secret = "change-me-please"
                # Token lifetime in milliseconds.
                # Shorter values reduce replay window.
                token_ttl_millis = 30000

                # --- Backend targets (proxy forwards to these servers) ---
                [[backends]]
                # Stable backend identifier used by routing/commands.
                id = "server-1"
                # Backend host/IP.
                host = "127.0.0.1"
                # Backend QUIC port.
                port = 30000
                # Certificate trust mode for proxy -> backend TLS.
                # "tofu"   = trust first seen cert and track rotations.
                # "strict" = require exact pinned fingerprint.
                # TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.
                cert_trust_mode = "tofu"
                # Expected SHA-256 certificate fingerprint (base64url).
                # Required when cert_trust_mode = "strict".
                # cert_fingerprint_sha256 = "BASE64URL_SHA256"

                # --- Routing defaults ---
                [routing]
                # Backend id used when no explicit target is selected.
                default_backend_id = "server-1"

                # --- Referral source injected into Connect packets ---
                [referral]
                # Host advertised to backend as source proxy host.
                host = "127.0.0.1"
                # Port advertised to backend as source proxy port.
                port = 25565

                # --- Protocol safety limits ---
                # TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.
                [limits]
                # Max bytes for language field in Connect packet.
                max_language_length = 16
                # Max bytes for identity token.
                max_identity_token_length = 8192
                # Max bytes for username.
                max_username_length = 16
                # Max bytes for referral payload.
                max_referral_data_length = 4096
                # Max bytes for host string fields.
                max_host_length = 256
                # Max accepted size for full Connect payload.
                max_connect_size = 38013

                # --- Rate limits (DoS/backpressure protection) ---
                # TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.
                [rate_limits]
                # Max concurrent handshake flows.
                handshake_concurrent_max = 256
                # Max concurrent routing decisions.
                routing_concurrent_max = 256
                # Max new connections per IP in time window.
                connection_per_ip_max = 20
                # Window for connection_per_ip_* in milliseconds.
                connection_per_ip_window_millis = 10000
                # Max handshake attempts per IP in time window.
                handshake_per_ip_max = 20
                # Window for handshake_per_ip_* in milliseconds.
                handshake_per_ip_window_millis = 10000
                # Max opened streams per session in time window.
                streams_per_session_max = 8
                # Window for streams_per_session_* in milliseconds.
                streams_per_session_window_millis = 10000
                # Max malformed/invalid packets per session in window.
                invalid_packets_per_session_max = 4
                # Window for invalid_packets_per_session_* in milliseconds.
                invalid_packets_per_session_window_millis = 10000

                # --- Messaging control-plane (proxy <-> backend-mod UDP) ---
                # TOUCH ONLY IF YOU KNOW WHAT YOU ARE DOING.
                [messaging]
                # Enable UDP messaging server for control-plane.
                enabled = false
                # UDP bind host for messaging.
                host = "0.0.0.0"
                # UDP bind port for messaging.
                port = 25570
                # Sender id stamped into control envelopes from proxy.
                control_sender_id = "proxy"
                # Max control message payload size in bytes.
                control_max_payload = 8192
                # Replay protection window in milliseconds.
                control_replay_window_millis = 10000
                # Max replay cache entries.
                control_replay_max_entries = 100000
                # Allowed sender clock skew in milliseconds.
                control_max_skew_millis = 120000
                # Envelope TTL in milliseconds.
                control_ttl_millis = 10000
                # Max concurrently processed inbound control messages.
                control_max_inflight = 256

                # --- Observability endpoint (/health, /metrics, /status) ---
                [observability]
                # Enable embedded HTTP observability server.
                enabled = false
                # HTTP bind host.
                host = "127.0.0.1"
                # HTTP bind port.
                port = 9100

                # --- Logging ---
                [logging]
                # Enable DEBUG log level globally.
                debug = false
                # Max archived log files kept in logs/archive/.
                max_archive_files = 10

                # --- Interactive console ---
                [console]
                # Max number of command history entries in .consolehistory.
                history_limit = 50
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
        validateUnknownKeys(result, TOP_LEVEL_ALLOWED_KEYS, "root")
        val schemaVersion = parseSchemaVersion(result)
        val listener = parseListener(result.getTable("listener"))
        val security = parseSecurity(result.getTable("security"))
        val backends = parseBackends(result.getArray("backends"))
        val routing = parseRouting(result.getTable("routing"))
        val limits = parseLimits(result.getTable("limits"))
        val referral = parseReferral(result.getTable("referral"), listener, limits)
        val rateLimits = parseRateLimits(result.getTable("rate_limits"))
        val messaging = parseMessaging(result.getTable("messaging"))
        val observability = parseObservability(result.getTable("observability"))
        val logging = parseLogging(result.getTable("logging"))
        val console = parseConsole(result.getTable("console"))

        val backendIds = backends.map { it.id }.toSet()
        if (routing.defaultBackendId !in backendIds) {
            throw ConfigException("routing.default_backend_id must reference an existing backend id")
        }
        validateNetworkConflicts(listener, messaging, observability, backends)

        return ProxyConfig(
            schemaVersion = schemaVersion,
            listener = listener,
            security = security,
            backends = backends,
            routing = routing,
            messaging = messaging,
            observability = observability,
            logging = logging,
            console = console,
            referral = referral,
            limits = limits,
            rateLimits = rateLimits,
        )
    }

    private fun validateNetworkConflicts(
        listener: ListenerConfig,
        messaging: MessagingConfig,
        observability: ObservabilityConfig,
        backends: List<BackendConfig>,
    ) {
        if (messaging.enabled && listener.port == messaging.port && hostBindingsOverlap(listener.host, messaging.host)) {
            throw ConfigException(
                "listener and messaging cannot share the same bind endpoint: ${listener.host}:${listener.port}"
            )
        }
        if (observability.enabled &&
            listener.port == observability.port &&
            hostBindingsOverlap(listener.host, observability.host)
        ) {
            throw ConfigException(
                "listener and observability cannot share the same bind endpoint: ${listener.host}:${listener.port}"
            )
        }
        if (observability.enabled &&
            messaging.enabled &&
            messaging.port == observability.port &&
            hostBindingsOverlap(messaging.host, observability.host)
        ) {
            throw ConfigException(
                "messaging and observability cannot share the same bind endpoint: ${messaging.host}:${messaging.port}"
            )
        }
        for (backend in backends) {
            if (listener.port == backend.port && hostBindingsOverlap(listener.host, backend.host)) {
                throw ConfigException(
                    "listener cannot share endpoint with backend '${backend.id}': ${listener.host}:${listener.port}"
                )
            }
            if (observability.enabled && backend.port == observability.port && hostBindingsOverlap(backend.host, observability.host)) {
                throw ConfigException(
                    "observability cannot share endpoint with backend '${backend.id}': ${observability.host}:${observability.port}"
                )
            }
        }
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
        validateUnknownKeys(listener, LISTENER_ALLOWED_KEYS, "listener")
        val host = requireString(listener, "host", "listener.host")
        val port = requirePort(listener, "port", "listener.port")
        return ListenerConfig(host = host, port = port)
    }

    private fun parseSecurity(table: TomlTable?): SecurityConfig {
        val security = table ?: throw ConfigException("Missing [security] section")
        validateUnknownKeys(security, SECURITY_ALLOWED_KEYS, "security")
        val proxySecret = requireString(security, "proxy_secret", "security.proxy_secret")
        SecretStrengthPolicy.validationError(proxySecret, "security.proxy_secret")?.let { message ->
            throw ConfigException(message)
        }
        val tokenTtl = requirePositiveLong(security, "token_ttl_millis", "security.token_ttl_millis")
        return SecurityConfig(proxySecret = proxySecret, tokenTtlMillis = tokenTtl)
    }

    private fun parseBackends(array: TomlArray?): List<BackendConfig> {
        val backendsArray = array ?: throw ConfigException("Missing [[backends]] array")
        if (backendsArray.isEmpty) {
            throw ConfigException("At least one backend must be configured")
        }
        val seenIds = mutableSetOf<String>()
        val seenEndpoints = mutableSetOf<String>()
        val backends = mutableListOf<BackendConfig>()
        for (i in 0 until backendsArray.size()) {
            val backend = backendsArray.getTable(i)
                ?: throw ConfigException("backends[$i] must be a table")
            validateUnknownKeys(backend, BACKEND_ALLOWED_KEYS, "backends[$i]")
            val id = requireString(backend, "id", "backends[$i].id")
            if (!seenIds.add(id)) {
                throw ConfigException("Duplicate backend id: $id")
            }
            val host = requireString(backend, "host", "backends[$i].host")
            if (host.trim().lowercase() in WILDCARD_BIND_HOSTS) {
                throw ConfigException("backends[$i].host must be a concrete routable host, not wildcard")
            }
            val port = requirePort(backend, "port", "backends[$i].port")
            val endpointKey = "${host.trim().lowercase()}:$port"
            if (!seenEndpoints.add(endpointKey)) {
                throw ConfigException("Duplicate backend endpoint: $host:$port")
            }
            val trustMode = parseBackendCertTrustMode(
                backend.getString("cert_trust_mode"),
                "backends[$i].cert_trust_mode",
            )
            val certFingerprint = backend.getString("cert_fingerprint_sha256")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val canonicalFingerprint = certFingerprint?.let {
                CertificateFingerprint.canonicalSha256Base64Url(it)
                    ?: throw ConfigException("backends[$i].cert_fingerprint_sha256 must be valid SHA-256 base64url")
            }
            if (trustMode == BackendCertTrustMode.STRICT_PINNED && canonicalFingerprint == null) {
                throw ConfigException("backends[$i].cert_fingerprint_sha256 is required when cert_trust_mode = \"strict\"")
            }
            backends.add(
                BackendConfig(
                    id = id,
                    host = host,
                    port = port,
                    certFingerprintSha256 = canonicalFingerprint,
                    certTrustMode = trustMode,
                )
            )
        }
        return backends
    }

    private fun parseBackendCertTrustMode(raw: String?, path: String): BackendCertTrustMode {
        val normalized = raw?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "", "tofu", "trust_on_first_use", "trust-on-first-use" -> BackendCertTrustMode.TOFU
            "strict", "strict_pinned", "strict-pinned" -> BackendCertTrustMode.STRICT_PINNED
            else -> throw ConfigException("$path must be one of: strict, tofu")
        }
    }

    private fun parseRouting(table: TomlTable?): RoutingConfig {
        val routing = table ?: throw ConfigException("Missing [routing] section")
        validateUnknownKeys(routing, ROUTING_ALLOWED_KEYS, "routing")
        val defaultBackendId = requireString(routing, "default_backend_id", "routing.default_backend_id")
        return RoutingConfig(defaultBackendId = defaultBackendId)
    }

    private fun parseMessaging(table: TomlTable?): MessagingConfig {
        val defaults = MessagingConfig(
            host = "0.0.0.0",
            port = 25570,
            enabled = false,
            controlSenderId = "proxy",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000,
            controlReplayMaxEntries = 100_000,
            controlMaxSkewMillis = 120_000,
            controlTtlMillis = 10_000,
            controlMaxInflight = 256,
        )
        if (table == null) {
            return defaults
        }
        validateUnknownKeys(table, MESSAGING_ALLOWED_KEYS, "messaging")
        val enabled = table.getBoolean("enabled") ?: defaults.enabled
        val host = table.getString("host") ?: defaults.host
        val port = table.getLong("port")?.toInt() ?: defaults.port
        val controlSenderId = table.getString("control_sender_id") ?: defaults.controlSenderId
        val controlMaxPayloadRaw = table.getLong("control_max_payload") ?: defaults.controlMaxPayload.toLong()
        val controlReplayWindowMillis = table.getLong("control_replay_window_millis") ?: defaults.controlReplayWindowMillis
        val controlReplayMaxEntriesRaw = table.getLong("control_replay_max_entries")
            ?: defaults.controlReplayMaxEntries.toLong()
        val controlMaxSkewMillis = table.getLong("control_max_skew_millis") ?: defaults.controlMaxSkewMillis
        val controlTtlMillis = table.getLong("control_ttl_millis") ?: defaults.controlTtlMillis
        val controlMaxInflightRaw = table.getLong("control_max_inflight") ?: defaults.controlMaxInflight.toLong()
        if (host.isBlank()) {
            throw ConfigException("messaging.host must not be blank")
        }
        if (port !in 1..65535) {
            throw ConfigException("messaging.port must be between 1 and 65535")
        }
        if (controlSenderId.isBlank()) {
            throw ConfigException("messaging.control_sender_id must not be blank")
        }
        if (controlMaxPayloadRaw <= 0 || controlMaxPayloadRaw > MAX_CONTROL_PAYLOAD_BYTES) {
            throw ConfigException(
                "messaging.control_max_payload must be in 1..$MAX_CONTROL_PAYLOAD_BYTES"
            )
        }
        if (controlReplayMaxEntriesRaw <= 0 || controlReplayMaxEntriesRaw > Int.MAX_VALUE) {
            throw ConfigException("messaging.control_replay_max_entries must be a positive integer")
        }
        if (controlMaxInflightRaw <= 0 || controlMaxInflightRaw > Int.MAX_VALUE) {
            throw ConfigException("messaging.control_max_inflight must be a positive integer")
        }
        val controlMaxPayload = controlMaxPayloadRaw.toInt()
        val controlReplayMaxEntries = controlReplayMaxEntriesRaw.toInt()
        val controlMaxInflight = controlMaxInflightRaw.toInt()
        if (controlReplayWindowMillis <= 0) {
            throw ConfigException("messaging.control_replay_window_millis must be > 0")
        }
        if (controlMaxSkewMillis < 0) {
            throw ConfigException("messaging.control_max_skew_millis must be >= 0")
        }
        if (controlTtlMillis <= 0) {
            throw ConfigException("messaging.control_ttl_millis must be > 0")
        }
        return MessagingConfig(
            host = host,
            port = port,
            enabled = enabled,
            controlSenderId = controlSenderId,
            controlMaxPayload = controlMaxPayload,
            controlReplayWindowMillis = controlReplayWindowMillis,
            controlReplayMaxEntries = controlReplayMaxEntries,
            controlMaxSkewMillis = controlMaxSkewMillis,
            controlTtlMillis = controlTtlMillis,
            controlMaxInflight = controlMaxInflight,
        )
    }

    private fun parseObservability(table: TomlTable?): ObservabilityConfig {
        val defaults = ObservabilityConfig()
        if (table == null) {
            return defaults
        }
        validateUnknownKeys(table, OBSERVABILITY_ALLOWED_KEYS, "observability")
        val enabled = table.getBoolean("enabled") ?: defaults.enabled
        val host = table.getString("host") ?: defaults.host
        val port = table.getLong("port") ?: defaults.port.toLong()
        if (host.isBlank()) {
            throw ConfigException("observability.host must not be blank")
        }
        if (port !in 1..65535) {
            throw ConfigException("observability.port must be between 1 and 65535")
        }
        return ObservabilityConfig(
            enabled = enabled,
            host = host,
            port = port.toInt(),
        )
    }

    private fun parseLogging(table: TomlTable?): LoggingConfig {
        val defaults = LoggingConfig()
        if (table == null) {
            return defaults
        }
        validateUnknownKeys(table, LOGGING_ALLOWED_KEYS, "logging")
        val debug = table.getBoolean("debug") ?: defaults.debug
        val maxArchiveFilesRaw = table.getLong("max_archive_files") ?: defaults.maxArchiveFiles.toLong()
        if (maxArchiveFilesRaw <= 0 || maxArchiveFilesRaw > Int.MAX_VALUE) {
            throw ConfigException("logging.max_archive_files must be a positive integer")
        }
        return LoggingConfig(
            debug = debug,
            maxArchiveFiles = maxArchiveFilesRaw.toInt(),
        )
    }

    private fun parseConsole(table: TomlTable?): ConsoleConfig {
        val defaults = ConsoleConfig()
        if (table == null) {
            return defaults
        }
        validateUnknownKeys(table, CONSOLE_ALLOWED_KEYS, "console")
        val historyLimitRaw = table.getLong("history_limit") ?: defaults.historyLimit.toLong()
        if (historyLimitRaw <= 0 || historyLimitRaw > Int.MAX_VALUE) {
            throw ConfigException("console.history_limit must be a positive integer")
        }
        return ConsoleConfig(
            historyLimit = historyLimitRaw.toInt(),
        )
    }

    private fun parseReferral(table: TomlTable?, listener: ListenerConfig, limits: ProtocolLimitsConfig): ReferralConfig {
        if (table == null) {
            return ReferralConfig(listener.host, listener.port)
        }
        validateUnknownKeys(table, REFERRAL_ALLOWED_KEYS, "referral")
        val host = requireString(table, "host", "referral.host")
        val port = requirePort(table, "port", "referral.port")
        if (host.toByteArray(StandardCharsets.UTF_8).size > limits.maxHostLength) {
            throw ConfigException("referral.host exceeds max length ${limits.maxHostLength}")
        }
        return ReferralConfig(host = host, port = port)
    }

    private fun parseLimits(table: TomlTable?): ProtocolLimitsConfig {
        if (table == null) {
            return ProtocolLimitsConfig()
        }
        validateUnknownKeys(table, LIMITS_ALLOWED_KEYS, "limits")
        val maxLanguage = requirePositiveInt(table, "max_language_length", "limits.max_language_length")
        val maxIdentity = requirePositiveInt(table, "max_identity_token_length", "limits.max_identity_token_length")
        val maxUsername = requirePositiveInt(table, "max_username_length", "limits.max_username_length")
        val maxReferral = requirePositiveInt(table, "max_referral_data_length", "limits.max_referral_data_length")
        val maxHost = requirePositiveInt(table, "max_host_length", "limits.max_host_length")
        val maxConnect = requirePositiveInt(table, "max_connect_size", "limits.max_connect_size")
        return ProtocolLimitsConfig(
            maxLanguageLength = maxLanguage,
            maxIdentityTokenLength = maxIdentity,
            maxUsernameLength = maxUsername,
            maxReferralDataLength = maxReferral,
            maxHostLength = maxHost,
            maxConnectSize = maxConnect,
        )
    }

    private fun parseRateLimits(table: TomlTable?): RateLimitConfig {
        if (table == null) {
            return RateLimitConfig(
                handshakeConcurrentMax = 256,
                routingConcurrentMax = 256,
                connectionPerIp = RateLimitWindow(20, 10_000),
                handshakePerIp = RateLimitWindow(20, 10_000),
                streamsPerSession = RateLimitWindow(8, 10_000),
                invalidPacketsPerSession = RateLimitWindow(4, 10_000),
            )
        }
        validateUnknownKeys(table, RATE_LIMITS_ALLOWED_KEYS, "rate_limits")
        val handshakeConcurrentMaxRaw = table.getLong("handshake_concurrent_max") ?: 256L
        if (handshakeConcurrentMaxRaw <= 0 || handshakeConcurrentMaxRaw > Int.MAX_VALUE) {
            throw ConfigException("rate_limits.handshake_concurrent_max must be a positive integer")
        }
        val handshakeConcurrentMax = handshakeConcurrentMaxRaw.toInt()
        val routingConcurrentMaxRaw = table.getLong("routing_concurrent_max") ?: 256L
        if (routingConcurrentMaxRaw <= 0 || routingConcurrentMaxRaw > Int.MAX_VALUE) {
            throw ConfigException("rate_limits.routing_concurrent_max must be a positive integer")
        }
        val routingConcurrentMax = routingConcurrentMaxRaw.toInt()
        val connection = parseRateLimitWindow(table, "connection_per_ip", "rate_limits.connection_per_ip")
        val handshake = parseRateLimitWindow(table, "handshake_per_ip", "rate_limits.handshake_per_ip")
        val streams = parseRateLimitWindow(table, "streams_per_session", "rate_limits.streams_per_session")
        val invalidPackets = parseRateLimitWindow(table, "invalid_packets_per_session", "rate_limits.invalid_packets_per_session")
        return RateLimitConfig(
            handshakeConcurrentMax = handshakeConcurrentMax,
            routingConcurrentMax = routingConcurrentMax,
            connectionPerIp = connection,
            handshakePerIp = handshake,
            streamsPerSession = streams,
            invalidPacketsPerSession = invalidPackets,
        )
    }

    private fun parseRateLimitWindow(table: TomlTable, prefix: String, path: String): RateLimitWindow {
        val maxEvents = requirePositiveInt(table, "${prefix}_max", "$path.max")
        val windowMillis = requirePositiveLong(table, "${prefix}_window_millis", "$path.window_millis")
        return RateLimitWindow(maxEvents, windowMillis)
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

    private fun requirePositiveInt(table: TomlTable, key: String, path: String): Int {
        val value = table.getLong(key)
            ?: throw ConfigException("Missing $path")
        if (value <= 0 || value > Int.MAX_VALUE) {
            throw ConfigException("$path must be a positive integer")
        }
        return value.toInt()
    }

    private fun requirePort(table: TomlTable, key: String, path: String): Int {
        val value = table.getLong(key)
            ?: throw ConfigException("Missing $path")
        if (value !in 1..65535) {
            throw ConfigException("$path must be between 1 and 65535")
        }
        return value.toInt()
    }

    private fun hostBindingsOverlap(left: String, right: String): Boolean {
        val leftHost = left.trim().lowercase()
        val rightHost = right.trim().lowercase()
        if (leftHost == rightHost) {
            return true
        }
        return leftHost in WILDCARD_BIND_HOSTS || rightHost in WILDCARD_BIND_HOSTS
    }

    private fun validateUnknownKeys(table: TomlTable, allowed: Set<String>, path: String) {
        val unknown = table.keySet()
            .filterNot { it in allowed }
            .sorted()
        if (unknown.isNotEmpty()) {
            throw ConfigException("Unknown keys in [$path]: ${unknown.joinToString(", ")}")
        }
    }

    private val WILDCARD_BIND_HOSTS = setOf("0.0.0.0", "::", "[::]")
    private const val MAX_CONTROL_PAYLOAD_BYTES = 65_535
    private val TOP_LEVEL_ALLOWED_KEYS = setOf(
        "schema_version",
        "listener",
        "security",
        "backends",
        "routing",
        "messaging",
        "observability",
        "logging",
        "console",
        "referral",
        "limits",
        "rate_limits",
    )
    private val LISTENER_ALLOWED_KEYS = setOf("host", "port")
    private val SECURITY_ALLOWED_KEYS = setOf("proxy_secret", "token_ttl_millis")
    private val BACKEND_ALLOWED_KEYS = setOf("id", "host", "port", "cert_fingerprint_sha256", "cert_trust_mode")
    private val ROUTING_ALLOWED_KEYS = setOf("default_backend_id")
    private val MESSAGING_ALLOWED_KEYS = setOf(
        "enabled",
        "host",
        "port",
        "control_sender_id",
        "control_max_payload",
        "control_replay_window_millis",
        "control_replay_max_entries",
        "control_max_skew_millis",
        "control_ttl_millis",
        "control_max_inflight",
    )
    private val OBSERVABILITY_ALLOWED_KEYS = setOf("enabled", "host", "port")
    private val LOGGING_ALLOWED_KEYS = setOf("debug", "max_archive_files")
    private val CONSOLE_ALLOWED_KEYS = setOf("history_limit")
    private val REFERRAL_ALLOWED_KEYS = setOf("host", "port")
    private val LIMITS_ALLOWED_KEYS = setOf(
        "max_language_length",
        "max_identity_token_length",
        "max_username_length",
        "max_referral_data_length",
        "max_host_length",
        "max_connect_size",
    )
    private val RATE_LIMITS_ALLOWED_KEYS = setOf(
        "handshake_concurrent_max",
        "routing_concurrent_max",
        "connection_per_ip_max",
        "connection_per_ip_window_millis",
        "handshake_per_ip_max",
        "handshake_per_ip_window_millis",
        "streams_per_session_max",
        "streams_per_session_window_millis",
        "invalid_packets_per_session_max",
        "invalid_packets_per_session_window_millis",
    )
}
