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
 * @property enforceProxy Whether invalid or missing proxy tokens should reject login.
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
    val enforceProxy: Boolean,
)
