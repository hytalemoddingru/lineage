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
import kotlin.math.max

class PlayerInfoCommand(
    private val players: PlayerManager,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "info"
    override val aliases: List<String> = listOf("player")
    override val description: String = "Show player diagnostic info"
    override val usage: String = "info <playerName|uuid>"
    override val permission: String? = "lineage.command.info"
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val language = (sender as? ProxyPlayerCommandSender)?.language
        val targetArg = context.args.firstOrNull()?.trim()
        val target = resolveTarget(sender as? ProxyPlayerCommandSender, targetArg)
        if (target == null) {
            if (targetArg.isNullOrEmpty()) {
                sender.sendMessage(messages.text(language, "info_usage", mapOf("usage" to usage)))
            } else {
                sender.sendMessage(messages.text(language, "info_player_not_found", mapOf("target" to targetArg)))
            }
            return
        }

        val connectedSeconds = max(0L, (System.currentTimeMillis() - target.connectedAtMillis) / 1000L)
        val backend = target.backendId ?: messages.text(language, "info_value_unknown")
        val ip = target.clientIp ?: messages.text(language, "info_value_unknown")
        val session = target.sessionId?.toString() ?: messages.text(language, "info_value_unknown")
        val version = target.clientVersion ?: messages.text(language, "info_value_unknown")
        val protocol = if (target.protocolCrc != null && target.protocolBuildNumber != null) {
            "${target.protocolCrc}/${target.protocolBuildNumber}"
        } else {
            messages.text(language, "info_value_unknown")
        }
        val ping = target.lastPingMillis?.let { "$it ms" } ?: messages.text(language, "info_value_unknown")

        val lines = listOf(
            messages.text(language, "info_header", mapOf("name" to target.username)),
            messages.text(language, "info_line_id", mapOf("value" to target.id.toString())),
            messages.text(language, "info_line_name", mapOf("value" to target.username)),
            messages.text(language, "info_line_language", mapOf("value" to target.language)),
            messages.text(language, "info_line_state", mapOf("value" to target.state.name)),
            messages.text(language, "info_line_backend", mapOf("value" to backend)),
            messages.text(language, "info_line_ip", mapOf("value" to ip)),
            messages.text(language, "info_line_session", mapOf("value" to session)),
            messages.text(language, "info_line_version", mapOf("value" to version)),
            messages.text(language, "info_line_protocol", mapOf("value" to protocol)),
            messages.text(language, "info_line_connected", mapOf("value" to "${connectedSeconds}s")),
            messages.text(language, "info_line_ping", mapOf("value" to ping)),
        )
        sender.sendMessage(lines.joinToString("\n"))
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

    private fun resolveTarget(sender: ProxyPlayerCommandSender?, targetArg: String?): ProxyPlayerImpl? {
        if (targetArg.isNullOrEmpty()) {
            if (sender == null) {
                return null
            }
            return players.get(sender.playerId) as? ProxyPlayerImpl
        }
        val uuid = runCatching { UUID.fromString(targetArg) }.getOrNull()
        if (uuid != null) {
            return players.get(uuid) as? ProxyPlayerImpl
        }
        return players.getByName(targetArg) as? ProxyPlayerImpl
    }
}
