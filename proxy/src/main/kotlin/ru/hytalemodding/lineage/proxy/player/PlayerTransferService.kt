/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.proxy.control.TransferRequestSender
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityStatus
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityTracker
import ru.hytalemodding.lineage.proxy.event.player.PlayerTransferEvent
import ru.hytalemodding.lineage.proxy.security.TransferTokenIssuer
import ru.hytalemodding.lineage.shared.control.TransferRequest
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import ru.hytalemodding.lineage.proxy.util.Logging
import java.util.UUID

/**
 * Coordinates transfer state and emits events.
 */
class PlayerTransferService(
    private val eventBus: EventBus,
    private val requestSender: TransferRequestSender?,
    private val tokenIssuer: TransferTokenIssuer?,
    knownBackendIds: Collection<String> = emptyList(),
    private val backendAvailabilityTracker: BackendAvailabilityTracker? = null,
) {
    private val logger = Logging.logger(PlayerTransferService::class.java)
    private val backendIdsByLower = knownBackendIds.associateBy { it.trim().lowercase() }

    fun transfer(player: ProxyPlayerImpl, targetBackendId: String) {
        val previous = player.backendId
        if (previous == targetBackendId) {
            return
        }
        logger.info(
            "{} changed server [{} -> {}]",
            player.username,
            previous ?: "unknown",
            targetBackendId,
        )
        player.applyTransfer(targetBackendId)
        eventBus.post(PlayerTransferEvent(player, previous, targetBackendId))
    }

    fun requestTransfer(player: ProxyPlayerImpl, targetBackendId: String): Boolean {
        return requestTransferDetailed(player, targetBackendId).accepted
    }

    fun requestTransferDetailed(player: ProxyPlayerImpl, targetBackendId: String): TransferRequestResult {
        val sender = requestSender ?: return TransferRequestResult(
            accepted = false,
            reason = TransferRequestFailureReason.CONTROL_PLANE_UNAVAILABLE,
        )
        val issuer = tokenIssuer ?: return TransferRequestResult(
            accepted = false,
            reason = TransferRequestFailureReason.CONTROL_PLANE_UNAVAILABLE,
        )
        val currentBackendId = player.backendId ?: return TransferRequestResult(
            accepted = false,
            reason = TransferRequestFailureReason.PLAYER_NOT_READY,
        )
        val normalizedTarget = normalizeBackendId(targetBackendId)
            ?: return TransferRequestResult(
                accepted = false,
                reason = TransferRequestFailureReason.BACKEND_NOT_FOUND,
            )
        if (currentBackendId == normalizedTarget) {
            return TransferRequestResult(
                accepted = false,
                reason = TransferRequestFailureReason.ALREADY_CONNECTED,
                targetBackendId = normalizedTarget,
            )
        }
        when (backendAvailabilityTracker?.status(normalizedTarget)) {
            BackendAvailabilityStatus.OFFLINE -> {
                return TransferRequestResult(
                    accepted = false,
                    reason = TransferRequestFailureReason.BACKEND_UNAVAILABLE,
                    targetBackendId = normalizedTarget,
                )
            }

            BackendAvailabilityStatus.UNKNOWN -> {
                return TransferRequestResult(
                    accepted = false,
                    reason = TransferRequestFailureReason.BACKEND_STATUS_UNKNOWN,
                    targetBackendId = normalizedTarget,
                )
            }

            BackendAvailabilityStatus.ONLINE, null -> Unit
        }
        val referralData = issuer.issueReferralData(player.id.toString(), normalizedTarget)
        val request = TransferRequest(
            correlationId = UUID.randomUUID(),
            playerId = player.id,
            targetBackendId = normalizedTarget,
            referralData = referralData,
        )
        if (!sender.sendTransferRequest(request)) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "transfer",
                    severity = "WARN",
                    reason = "REQUEST_SEND_FAILED",
                    correlationId = request.correlationId.toString(),
                    fields = mapOf(
                        "playerId" to request.playerId,
                        "targetBackendId" to request.targetBackendId,
                    ),
                )
            )
            return TransferRequestResult(
                accepted = false,
                reason = TransferRequestFailureReason.REQUEST_SEND_FAILED,
                targetBackendId = normalizedTarget,
            )
        }
        logger.info(
            "{} requested server change [{} -> {}]",
            player.username,
            currentBackendId,
            normalizedTarget,
        )
        logger.debug(
            "{}",
            StructuredLog.event(
                category = "transfer",
                severity = "DEBUG",
                reason = "REQUEST_SENT",
                correlationId = request.correlationId.toString(),
                fields = mapOf(
                    "playerId" to request.playerId,
                    "targetBackendId" to request.targetBackendId,
                ),
            )
        )
        player.applyTransfer(normalizedTarget)
        return TransferRequestResult(
            accepted = true,
            targetBackendId = normalizedTarget,
        )
    }

    private fun normalizeBackendId(candidate: String): String? {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (backendIdsByLower.isEmpty()) {
            return trimmed
        }
        return backendIdsByLower[trimmed.lowercase()]
    }
}

data class TransferRequestResult(
    val accepted: Boolean,
    val reason: TransferRequestFailureReason? = null,
    val targetBackendId: String? = null,
)

enum class TransferRequestFailureReason {
    CONTROL_PLANE_UNAVAILABLE,
    PLAYER_NOT_READY,
    BACKEND_NOT_FOUND,
    ALREADY_CONNECTED,
    BACKEND_UNAVAILABLE,
    BACKEND_STATUS_UNKNOWN,
    REQUEST_SEND_FAILED,
}
