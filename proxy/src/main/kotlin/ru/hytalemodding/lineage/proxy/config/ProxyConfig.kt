/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.config

import ru.hytalemodding.lineage.shared.protocol.ProtocolLimitsConfig

/**
 * Current config schema version.
 */
const val CURRENT_CONFIG_SCHEMA_VERSION = 1

/**
 * Root configuration for the proxy.
 *
 * @property schemaVersion Schema version of the configuration file.
 * @property listener Network listener configuration for client connections.
 * @property security Security-related settings such as proxy secret and TTL.
 * @property backends List of available backend servers.
 * @property routing Routing defaults and rules.
 * @property messaging UDP messaging configuration for backend communication.
 * @property observability HTTP endpoints for runtime health/metrics.
 * @property logging Runtime logging options.
 * @property console Interactive console options.
 * @property referral Referral source configuration injected into Connect packets.
 * @property limits Protocol limit overrides for client sanity checks.
 * @property rateLimits Basic abuse protection thresholds.
 */
data class ProxyConfig(
    val schemaVersion: Int,
    val listener: ListenerConfig,
    val security: SecurityConfig,
    val backends: List<BackendConfig>,
    val routing: RoutingConfig,
    val messaging: MessagingConfig,
    val observability: ObservabilityConfig = ObservabilityConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val console: ConsoleConfig = ConsoleConfig(),
    val referral: ReferralConfig,
    val limits: ProtocolLimitsConfig,
    val rateLimits: RateLimitConfig,
)

/**
 * Listener configuration for incoming client connections.
 *
 * @property host Bind host or IP address.
 * @property port Bind port for the proxy listener.
 */
data class ListenerConfig(
    val host: String,
    val port: Int,
)

/**
 * Security configuration for proxy token issuance.
 *
 * @property proxySecret HMAC secret used to sign proxy tokens.
 * @property tokenTtlMillis Token time-to-live in milliseconds (keep short to limit replay window).
 */
data class SecurityConfig(
    val proxySecret: String,
    val tokenTtlMillis: Long,
)

/**
 * Backend server definition.
 *
 * @property id Stable backend identifier referenced by routing rules.
 * @property host Backend host or IP address.
 * @property port Backend port.
 * @property certFingerprintSha256 Backend TLS certificate SHA-256 fingerprint in base64url.
 * Required in strict mode, optional in TOFU mode.
 * @property certTrustMode Certificate trust mode for proxy->backend QUIC.
 */
data class BackendConfig(
    val id: String,
    val host: String,
    val port: Int,
    val certFingerprintSha256: String? = null,
    val certTrustMode: BackendCertTrustMode = BackendCertTrustMode.TOFU,
)

/**
 * Backend certificate trust behavior.
 */
enum class BackendCertTrustMode {
    /**
     * Require exact SHA-256 pin match from config.
     */
    STRICT_PINNED,

    /**
     * Trust first seen certificate and accept rotations with warning.
     */
    TOFU,
}

/**
 * Routing defaults for selecting backend servers.
 *
 * @property defaultBackendId Backend identifier used when no rule matches.
 */
data class RoutingConfig(
    val defaultBackendId: String,
)

/**
 * Messaging configuration for proxy control channel.
 *
 * @property host Bind host for UDP messaging.
 * @property port Bind port for UDP messaging.
 * @property enabled Whether messaging server is enabled.
 * @property controlSenderId Sender identifier for control-plane envelopes.
 * @property controlMaxPayload Max payload size for control-plane messages.
 * @property controlReplayWindowMillis Replay window for control-plane messages.
 * @property controlReplayMaxEntries Max replay entries kept in memory.
 * @property controlMaxSkewMillis Allowed clock skew for control-plane messages.
 * @property controlTtlMillis TTL for control-plane envelopes.
 * @property controlMaxInflight Max concurrently processed inbound control-plane envelopes.
 */
data class MessagingConfig(
    val host: String,
    val port: Int,
    val enabled: Boolean,
    val controlSenderId: String,
    val controlMaxPayload: Int,
    val controlReplayWindowMillis: Long,
    val controlReplayMaxEntries: Int,
    val controlMaxSkewMillis: Long,
    val controlTtlMillis: Long,
    val controlMaxInflight: Int = 256,
)

/**
 * HTTP observability endpoint configuration.
 */
data class ObservabilityConfig(
    val enabled: Boolean = false,
    val host: String = "127.0.0.1",
    val port: Int = 9100,
)

/**
 * Logging configuration.
 */
data class LoggingConfig(
    val debug: Boolean = false,
    val maxArchiveFiles: Int = 10,
)

/**
 * Interactive console configuration.
 */
data class ConsoleConfig(
    val historyLimit: Int = 50,
)

/**
 * Referral source configuration used in Connect packet injection.
 */
data class ReferralConfig(
    val host: String,
    val port: Int,
)

/**
 * Rate limiting configuration for basic abuse protection.
 */
data class RateLimitConfig(
    val handshakeConcurrentMax: Int,
    val routingConcurrentMax: Int,
    val connectionPerIp: RateLimitWindow,
    val handshakePerIp: RateLimitWindow,
    val streamsPerSession: RateLimitWindow,
    val invalidPacketsPerSession: RateLimitWindow,
)

/**
 * Fixed window rate limit definition.
 */
data class RateLimitWindow(
    val maxEvents: Int,
    val windowMillis: Long,
)
