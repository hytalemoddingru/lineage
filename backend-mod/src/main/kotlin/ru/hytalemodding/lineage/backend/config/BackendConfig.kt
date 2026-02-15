/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.config

/**
 * Current backend configuration schema version.
 */
const val CURRENT_BACKEND_CONFIG_SCHEMA_VERSION = 1

/**
 * Backend-mod configuration.
 *
 * @property schemaVersion Schema version of the configuration file.
 * @property serverId Backend server identifier expected by the proxy.
 * @property proxySecret Shared secret used to validate proxy tokens.
 * @property previousProxySecret Optional previous secret accepted during rotation.
 * @property proxyConnectHost Proxy host used for client referrals.
 * @property proxyConnectPort Proxy port used for client referrals.
 * @property messagingHost Proxy host for UDP messaging control channel.
 * @property messagingPort Proxy port for UDP messaging control channel.
 * @property messagingEnabled Whether messaging client should be enabled.
 * @property controlSenderId Sender identifier for control-plane envelopes.
 * @property controlMaxPayload Max payload size for control-plane messages.
 * @property controlReplayWindowMillis Replay window for control-plane messages.
 * @property controlReplayMaxEntries Max replay entries kept in memory.
 * @property controlMaxSkewMillis Allowed clock skew for control-plane messages.
 * @property controlTtlMillis TTL for control-plane envelopes.
 * @property controlMaxInflight Max concurrently processed inbound control-plane envelopes.
 * @property controlExpectedSenderId Expected sender identifier for inbound control-plane envelopes.
 * @property requireAuthenticatedMode Whether auth-mode must be AUTHENTICATED.
 * @property enforceProxy Whether invalid or missing proxy tokens should reject login.
 * @property referralSourceHost Expected referral source host for proxy connections.
 * @property referralSourcePort Expected referral source port for proxy connections.
 * @property replayWindowMillis Time window for replay protection.
 * @property replayMaxEntries Maximum replay entries kept in memory.
 */
data class BackendConfig(
    val schemaVersion: Int,
    val serverId: String,
    val proxySecret: String,
    val previousProxySecret: String?,
    val proxyConnectHost: String,
    val proxyConnectPort: Int,
    val messagingHost: String,
    val messagingPort: Int,
    val messagingEnabled: Boolean,
    val controlSenderId: String,
    val controlMaxPayload: Int,
    val controlReplayWindowMillis: Long,
    val controlReplayMaxEntries: Int,
    val controlMaxSkewMillis: Long,
    val controlTtlMillis: Long,
    val controlMaxInflight: Int = 256,
    val controlExpectedSenderId: String = "proxy",
    val requireAuthenticatedMode: Boolean,
    val enforceProxy: Boolean,
    val referralSourceHost: String,
    val referralSourcePort: Int,
    val replayWindowMillis: Long,
    val replayMaxEntries: Int,
)
