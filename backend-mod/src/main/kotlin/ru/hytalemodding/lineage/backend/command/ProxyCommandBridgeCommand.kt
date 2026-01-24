/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.command

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.hypixel.hytale.server.core.entity.entities.Player
import ru.hytalemodding.lineage.backend.messaging.BackendMessaging
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol
import ru.hytalemodding.lineage.shared.command.ProxyCommandFlags

class ProxyCommandBridgeCommand(
    name: String,
    description: String,
    private val usage: String,
    private val aliases: List<String>,
    private val flags: Int,
    private val isMessagingEnabled: () -> Boolean,
) : CommandBase(name, description) {
    init {
        setAllowsExtraArguments(true)
        if (aliases.isNotEmpty()) {
            addAliases(*aliases.toTypedArray())
        }
    }

    override fun canGeneratePermission(): Boolean = false

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            if (flags and ProxyCommandFlags.PLAYER_ONLY != 0) {
                context.sendMessage(Message.raw("Command is only available to players."))
                return
            }
            context.sendMessage(Message.raw("Command is only available to players."))
            return
        }
        if (!isMessagingEnabled()) {
            context.sendMessage(Message.raw("Proxy messaging is disabled."))
            return
        }
        val input = context.inputString.trim()
        if (input.isBlank()) {
            context.sendMessage(Message.raw("Usage: $usage"))
            return
        }
        val player = context.senderAs(Player::class.java)
        val payload = PlayerCommandProtocol.encodeRequest(player.playerRef.uuid, input)
        BackendMessaging.send(PlayerCommandProtocol.REQUEST_CHANNEL_ID, payload)
    }
}
