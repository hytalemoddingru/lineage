/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.api.player.ProxyPlayer
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl
import ru.hytalemodding.lineage.proxy.player.ProxySystemMessageFormatter
import ru.hytalemodding.lineage.proxy.text.RenderLimits
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol
import java.util.UUID

/**
 * Command sender implementation backed by a proxy player.
 */
class ProxyPlayerCommandSender(
    private val player: ProxyPlayer,
    private val responder: CommandResponder,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
    private val renderLimitsProvider: () -> RenderLimits = { RenderLimits() },
) : CommandSender {
    val playerId: UUID
        get() = player.id

    override val name: String
        get() = player.username

    override val type: SenderType = SenderType.PLAYER
    val language: String?
        get() = (player as? ProxyPlayerImpl)?.language

    override fun sendMessage(message: String) {
        val formatted = if (player is ProxyPlayerImpl) {
            ProxySystemMessageFormatter.format(player.language, message, messages, renderLimitsProvider())
        } else {
            message
        }
        responder.send(player.id, formatted)
    }
}

/**
 * Sends command responses back to the backend via messaging.
 */
class CommandResponder(
    private val channel: ru.hytalemodding.lineage.api.messaging.Channel,
) {
    fun send(playerId: UUID, message: String) {
        val payload = PlayerCommandProtocol.encodeResponse(playerId, message)
        channel.send(payload)
    }
}
