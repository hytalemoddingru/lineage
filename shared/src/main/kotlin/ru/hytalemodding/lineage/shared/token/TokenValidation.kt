/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.token

/**
 * Validation error categories for tokens.
 */
enum class TokenValidationError {
    MALFORMED,
    INVALID_SIGNATURE,
    EXPIRED,
    NOT_YET_VALID,
    UNSUPPORTED_VERSION,
    TARGET_MISMATCH,
}

/**
 * Thrown when validation fails with a specific [error].
 */
class TokenValidationException(
    val error: TokenValidationError,
    message: String,
) : RuntimeException(message)
