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
import ru.hytalemodding.lineage.proxy.event.player.PlayerTransferEvent
import ru.hytalemodding.lineage.proxy.security.TransferTokenIssuer
import ru.hytalemodding.lineage.shared.control.TransferRequest
import java.util.UUID

/**
 * Coordinates transfer state and emits events.
 */
class PlayerTransferService(
    private val eventBus: EventBus,
    private val requestSender: TransferRequestSender?,
    private val tokenIssuer: TransferTokenIssuer?,
) {
    fun transfer(player: ProxyPlayerImpl, targetBackendId: String) {
        val previous = player.backendId
        if (previous == targetBackendId) {
            return
        }
        player.applyTransfer(targetBackendId)
        eventBus.post(PlayerTransferEvent(player, previous, targetBackendId))
    }

    fun requestTransfer(player: ProxyPlayerImpl, targetBackendId: String): Boolean {
        val sender = requestSender ?: return false
        val issuer = tokenIssuer ?: return false
        val currentBackendId = player.backendId ?: return false
        if (currentBackendId == targetBackendId) {
            return false
        }
        val referralData = issuer.issueReferralData(player.id.toString(), targetBackendId)
        val request = TransferRequest(
            correlationId = UUID.randomUUID(),
            playerId = player.id,
            targetBackendId = targetBackendId,
            referralData = referralData,
        )
        if (!sender.sendTransferRequest(request)) {
            return false
        }
        player.applyTransfer(targetBackendId)
        return true
    }
}
