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
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl
import java.util.UUID

class PingCommand(
    private val players: PlayerManager,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "ping"
    override val aliases: List<String> = emptyList()
    override val description: String = "Show QUIC latency in milliseconds"
    override val usage: String = "ping [playerName|uuid]"
    override val permission: String? = null
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val senderPlayer = sender as? ProxyPlayerCommandSender
        val language = senderPlayer?.language
        val targetArg = context.args.firstOrNull()?.trim()
        val target = resolveTarget(senderPlayer, targetArg)
        if (target == null) {
            if (targetArg.isNullOrEmpty()) {
                sender.sendMessage(messages.text(language, "ping_usage", mapOf("usage" to usage)))
            } else {
                sender.sendMessage(messages.text(language, "ping_player_not_found", mapOf("target" to targetArg)))
            }
            return
        }

        target.requestPingMillis { ping ->
            if (ping == null) {
                sender.sendMessage(messages.text(language, "ping_unavailable", mapOf("name" to target.username)))
                return@requestPingMillis
            }
            if (senderPlayer != null && senderPlayer.playerId == target.id) {
                sender.sendMessage(messages.text(language, "ping_result_self", mapOf("ping" to ping.toString())))
                return@requestPingMillis
            }
            sender.sendMessage(
                messages.text(
                    language,
                    "ping_result_player",
                    mapOf("name" to target.username, "ping" to ping.toString()),
                )
            )
        }
    }

    override fun suggest(context: CommandContext): List<String> {
        if (context.args.size > 1) {
            return emptyList()
        }
        val query = context.args.firstOrNull()?.trim()?.lowercase().orEmpty()
        return players.all()
            .map { it.username }
            .distinct()
            .sorted()
            .filter { it.lowercase().startsWith(query) }
    }

    private fun resolveTarget(senderPlayer: ProxyPlayerCommandSender?, targetArg: String?): ProxyPlayerImpl? {
        if (targetArg.isNullOrEmpty()) {
            if (senderPlayer == null) {
                return null
            }
            return players.get(senderPlayer.playerId) as? ProxyPlayerImpl
        }
        val uuid = runCatching { UUID.fromString(targetArg) }.getOrNull()
        if (uuid != null) {
            return players.get(uuid) as? ProxyPlayerImpl
        }
        return players.getByName(targetArg) as? ProxyPlayerImpl
    }
}
