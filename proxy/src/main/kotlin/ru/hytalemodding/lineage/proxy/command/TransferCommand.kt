/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandFlag
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.proxy.player.PlayerTransferService
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl

class TransferCommand(
    private val players: PlayerManager,
    private val transferService: PlayerTransferService,
) : Command {
    override val name: String = "transfer"
    override val aliases: List<String> = emptyList()
    override val description: String = "Request transfer to another backend"
    override val usage: String = "transfer <backendId>"
    override val permission: String? = "lineage.command.transfer"
    override val flags: Set<CommandFlag> = setOf(CommandFlag.PLAYER_ONLY)

    override fun execute(context: CommandContext) {
        val sender = context.sender
        if (sender.type != SenderType.PLAYER) {
            sender.sendMessage("Command is only available to players.")
            return
        }
        val target = context.args.firstOrNull()?.trim()
        if (target.isNullOrEmpty()) {
            sender.sendMessage("Usage: $usage")
            return
        }
        val player = players.getByName(sender.name) as? ProxyPlayerImpl
        if (player == null) {
            sender.sendMessage("Player not found.")
            return
        }
        if (!transferService.requestTransfer(player, target)) {
            sender.sendMessage("Transfer failed.")
        }
    }

    override fun suggest(context: CommandContext): List<String> = emptyList()
}
