/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.control

import java.util.UUID

enum class TransferResultStatus {
    OK,
    FAILED,
}

enum class TransferFailureReason {
    BACKEND_NOT_FOUND,
    REFERRAL_REJECTED,
    INTERNAL_ERROR,
}

data class TransferResult(
    val correlationId: UUID,
    val status: TransferResultStatus,
    val reason: TransferFailureReason?,
)
