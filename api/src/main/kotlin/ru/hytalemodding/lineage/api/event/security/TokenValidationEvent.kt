/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.event.security

import ru.hytalemodding.lineage.api.event.Event
import java.util.UUID

enum class TokenValidationResult {
    ACCEPTED,
    REJECTED,
}

enum class TokenValidationReason {
    MALFORMED,
    INVALID_SIGNATURE,
    EXPIRED,
    NOT_YET_VALID,
    UNSUPPORTED_VERSION,
    TARGET_MISMATCH,
    REPLAYED,
}

data class TokenValidationEvent(
    val playerId: UUID,
    val backendId: String,
    val result: TokenValidationResult,
    val reason: TokenValidationReason?,
) : Event
