/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.protocol

/**
 * Centralized protocol limits derived from the Hytale Connect packet schema.
 *
 * Policy note:
 * - Values matching server hard caps (security): MAX_* and CONNECT_MAX_SIZE.
 * - Values safe to relax for compatibility (soft): use ProtocolLimitsConfig overrides.
 */
object ProtocolLimits {
    const val PROTOCOL_HASH_LENGTH = 64
    const val MAX_LANGUAGE_LENGTH = 128
    const val MAX_IDENTITY_TOKEN_LENGTH = 8192
    const val MAX_USERNAME_LENGTH = 16
    const val MAX_REFERRAL_DATA_LENGTH = 4096
    const val MAX_HOST_LENGTH = 256

    const val CONNECT_FIXED_BLOCK_SIZE = 82
    const val CONNECT_VARIABLE_FIELD_COUNT = 5
    const val CONNECT_VARIABLE_BLOCK_START = 102
    const val CONNECT_MAX_SIZE = 38161
}

data class ProtocolLimitsConfig(
    val maxLanguageLength: Int = ProtocolLimits.MAX_LANGUAGE_LENGTH,
    val maxIdentityTokenLength: Int = ProtocolLimits.MAX_IDENTITY_TOKEN_LENGTH,
    val maxUsernameLength: Int = ProtocolLimits.MAX_USERNAME_LENGTH,
    val maxReferralDataLength: Int = ProtocolLimits.MAX_REFERRAL_DATA_LENGTH,
    val maxHostLength: Int = ProtocolLimits.MAX_HOST_LENGTH,
    val maxConnectSize: Int = ProtocolLimits.CONNECT_MAX_SIZE,
)
