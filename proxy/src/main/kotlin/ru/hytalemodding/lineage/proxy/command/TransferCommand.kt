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
import ru.hytalemodding.lineage.proxy.config.BackendConfig
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityStatus
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityTracker
import ru.hytalemodding.lineage.proxy.player.PlayerTransferService
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl
import ru.hytalemodding.lineage.proxy.player.TransferRequestFailureReason

class TransferCommand(
    private val players: PlayerManager,
    private val transferService: PlayerTransferService,
    private val backends: List<BackendConfig>,
    private val availabilityTracker: BackendAvailabilityTracker?,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "transfer"
    override val aliases: List<String> = listOf("server", "ser")
    override val description: String = "Request transfer to another backend"
    override val usage: String = "transfer <backendId|list> [playerName]"
    override val permission: String? = "lineage.command.transfer"
    override val flags: Set<CommandFlag> = emptySet()

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val language = (sender as? ProxyPlayerCommandSender)?.language
        val target = context.args.firstOrNull()?.trim()
        if (target.isNullOrEmpty()) {
            sender.sendMessage(messages.text(language, "transfer_usage", mapOf("usage" to usage)))
            return
        }
        if (target.equals("list", ignoreCase = true)) {
            sendBackendList(sender, language)
            return
        }
        val playerName = context.args.getOrNull(1)?.trim()
        val player = resolveTargetPlayer(context, playerName)
        if (player == null) {
            if (playerName.isNullOrEmpty()) {
                sender.sendMessage(messages.text(language, "transfer_usage_console"))
            } else {
                sender.sendMessage(messages.text(language, "transfer_player_not_found", mapOf("playerName" to playerName)))
            }
            return
        }
        val result = transferService.requestTransferDetailed(player, target)
        if (result.accepted) {
            sender.sendMessage(
                messages.text(
                    language,
                    "transfer_requested",
                    mapOf(
                        "playerName" to player.username,
                        "backendId" to (result.targetBackendId ?: target),
                    ),
                )
            )
            return
        }
        when (result.reason) {
            TransferRequestFailureReason.BACKEND_NOT_FOUND -> {
                sender.sendMessage(messages.text(language, "transfer_unknown_backend"))
            }

            TransferRequestFailureReason.BACKEND_UNAVAILABLE -> {
                sender.sendMessage(
                    messages.text(
                        language,
                        "transfer_backend_offline",
                        mapOf("backendId" to (result.targetBackendId ?: target)),
                    )
                )
            }

            TransferRequestFailureReason.BACKEND_STATUS_UNKNOWN -> {
                sender.sendMessage(
                    messages.text(
                        language,
                        "transfer_backend_unknown_status",
                        mapOf("backendId" to (result.targetBackendId ?: target)),
                    )
                )
            }

            TransferRequestFailureReason.ALREADY_CONNECTED -> {
                sender.sendMessage(
                    messages.text(
                        language,
                        "transfer_already_connected",
                        mapOf("backendId" to (result.targetBackendId ?: target)),
                    )
                )
            }

            TransferRequestFailureReason.PLAYER_NOT_READY -> {
                sender.sendMessage(messages.text(language, "transfer_player_not_ready"))
            }

            TransferRequestFailureReason.CONTROL_PLANE_UNAVAILABLE -> {
                sender.sendMessage(messages.text(language, "transfer_control_unavailable"))
            }

            TransferRequestFailureReason.REQUEST_SEND_FAILED -> {
                sender.sendMessage(messages.text(language, "transfer_request_failed"))
            }

            null -> {
                sender.sendMessage(messages.text(language, "transfer_failed"))
            }
        }
    }

    private fun resolveTargetPlayer(context: CommandContext, explicitName: String?): ProxyPlayerImpl? {
        if (!explicitName.isNullOrEmpty()) {
            return players.getByName(explicitName) as? ProxyPlayerImpl
        }
        val sender = context.sender
        val senderPlayer = sender as? ProxyPlayerCommandSender
        if (senderPlayer != null) {
            return players.get(senderPlayer.playerId) as? ProxyPlayerImpl
        }
        return null
    }

    override fun suggest(context: CommandContext): List<String> {
        if (context.args.isEmpty()) {
            return listOf("list") + backends.map { it.id }
        }
        if (context.args.size == 1) {
            val query = context.args[0].trim().lowercase()
            val candidates = mutableListOf("list")
            candidates.addAll(backends.map { it.id })
            return if (query.isEmpty()) candidates else candidates.filter { it.lowercase().startsWith(query) }
        }
        if (context.args.size == 2) {
            val query = context.args[1].trim().lowercase()
            val names = players.all()
                .map { it.username }
                .distinct()
                .sorted()
            return if (query.isEmpty()) names else names.filter { it.lowercase().startsWith(query) }
        }
        return emptyList()
    }

    private fun sendBackendList(
        sender: ru.hytalemodding.lineage.api.command.CommandSender,
        language: String?,
    ) {
        val lines = mutableListOf<String>()
        lines.add(messages.text(language, "transfer_backends_header"))
        for (backend in backends.sortedBy { it.id }) {
            val status = availabilityTracker?.status(backend.id) ?: BackendAvailabilityStatus.UNKNOWN
            val statusLabel = when (status) {
                BackendAvailabilityStatus.ONLINE -> messages.text(language, "transfer_status_online")
                BackendAvailabilityStatus.OFFLINE -> messages.text(language, "transfer_status_offline")
                BackendAvailabilityStatus.UNKNOWN -> messages.text(language, "transfer_status_unknown")
            }
            lines.add(
                messages.text(
                    language,
                    "transfer_backends_entry",
                    mapOf(
                        "backendId" to backend.id,
                        "endpoint" to "${backend.host}:${backend.port}",
                        "status" to statusLabel,
                    ),
                )
            )
        }
        sender.sendMessage(lines.joinToString("\n"))
    }
}
