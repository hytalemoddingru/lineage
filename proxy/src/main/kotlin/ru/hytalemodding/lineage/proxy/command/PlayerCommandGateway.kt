/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.messaging.Message
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol

/**
 * Bridges player command requests from the backend to the proxy command dispatcher.
 */
class PlayerCommandGateway(
    messaging: Messaging,
    private val dispatcher: CommandDispatcher,
    private val players: PlayerManager,
) {
    private val responseChannel = messaging.channel(PlayerCommandProtocol.RESPONSE_CHANNEL_ID)
        ?: messaging.registerChannel(PlayerCommandProtocol.RESPONSE_CHANNEL_ID, MessageHandler { })
    private val responder = CommandResponder(responseChannel)

    init {
        messaging.registerChannel(PlayerCommandProtocol.REQUEST_CHANNEL_ID, MessageHandler { message ->
            handleRequest(message)
        })
    }

    private fun handleRequest(message: Message) {
        val request = PlayerCommandProtocol.decodeRequest(message.payload) ?: return
        val player = players.get(request.playerId)
        if (player == null) {
            responder.send(request.playerId, "Player not found.")
            return
        }
        val sender: CommandSender = ProxyPlayerCommandSender(player, responder)
        val normalized = normalizeInput(request.command)
        if (normalized.isEmpty()) {
            sender.sendMessage("Command must not be empty.")
            return
        }
        val handled = dispatcher.dispatch(sender, normalized)
        if (!handled) {
            sender.sendMessage("Unknown command.")
        }
    }

    private fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("/")) {
            return trimmed.removePrefix("/").trim()
        }
        return trimmed
    }
}
