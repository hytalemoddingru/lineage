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
import com.hypixel.hytale.server.core.command.system.CommandUtil
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.hypixel.hytale.server.core.entity.entities.Player
import ru.hytalemodding.lineage.backend.messaging.BackendMessaging
import ru.hytalemodding.lineage.backend.transfer.TransferTokenIssuer
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol

/**
 * Forwards player commands to the proxy over the messaging channel.
 */
class LineageProxyCommand(
    private val isMessagingEnabled: () -> Boolean,
    private val transferIssuer: TransferTokenIssuer,
    private val proxyHost: String,
    private val proxyPort: Int,
) : CommandBase("lineage", "lineage.command.proxy.desc") {
    init {
        setAllowsExtraArguments(true)
        addAliases("proxy")
    }

    override fun canGeneratePermission(): Boolean = false

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Command is only available to players."))
            return
        }
        val rawArgs = CommandUtil.stripCommandName(context.inputString).trim()
        if (rawArgs.isBlank()) {
            context.sendMessage(Message.raw("Usage: /lineage <command> | /lineage transfer <backendId>"))
            return
        }
        val firstToken = rawArgs.split(Regex("\\s+"), limit = 2)[0]
        if (firstToken.equals("transfer", ignoreCase = true)) {
            handleTransfer(context, rawArgs.removePrefix(firstToken).trim())
            return
        }
        if (!isMessagingEnabled()) {
            context.sendMessage(Message.raw("Proxy messaging is disabled."))
            return
        }
        val player = context.senderAs(Player::class.java)
        val playerRef = player.playerRef
        val payload = PlayerCommandProtocol.encodeRequest(playerRef.uuid, rawArgs)
        BackendMessaging.send(PlayerCommandProtocol.REQUEST_CHANNEL_ID, payload)
    }

    private fun handleTransfer(context: CommandContext, rawArgs: String) {
        val target = rawArgs.trim()
        if (target.isBlank()) {
            context.sendMessage(Message.raw("Usage: /lineage transfer <backendId>"))
            return
        }
        val player = context.senderAs(Player::class.java)
        val playerRef = player.playerRef
        val referralData = transferIssuer.issueReferralData(playerRef.uuid.toString(), target)
        playerRef.referToServer(proxyHost, proxyPort, referralData)
        context.sendMessage(Message.raw("Transferring to $target..."))
    }
}
