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

class ListPlayersCommand(
    private val players: PlayerManager,
    backendIds: Collection<String>,
    private val messages: ProxyMessages = ProxyMessagesLoader.defaults(),
) : Command {
    override val name: String = "list"
    override val aliases: List<String> = listOf("online", "players")
    override val description: String = "Show online players with pagination"
    override val usage: String = "list [backendId] [page]"
    override val permission: String? = null
    override val flags: Set<CommandFlag> = emptySet()

    private val knownBackends = backendIds.toSet()

    override fun execute(context: CommandContext) {
        val sender = context.sender
        val language = (sender as? ProxyPlayerCommandSender)?.language
        val query = parseQuery(context.args)
        if (query == null) {
            sender.sendMessage(messages.text(language, "list_usage", mapOf("usage" to usage)))
            return
        }
        val backendId = query.backendId
        if (backendId != null && backendId !in knownBackends) {
            sender.sendMessage(messages.text(language, "list_unknown_backend", mapOf("backendId" to backendId)))
            return
        }

        val filtered = players.all()
            .asSequence()
            .filter { backendId == null || it.backendId == backendId }
            .sortedBy { it.username.lowercase() }
            .toList()

        if (filtered.isEmpty()) {
            val key = if (backendId == null) "list_none" else "list_none_backend"
            val vars = if (backendId == null) emptyMap() else mapOf("backendId" to backendId)
            sender.sendMessage(messages.text(language, key, vars))
            return
        }

        val totalPages = ((filtered.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        val page = query.page
        if (page < 1 || page > totalPages) {
            sender.sendMessage(
                messages.text(
                    language,
                    "list_page_invalid",
                    mapOf("page" to page.toString(), "totalPages" to totalPages.toString()),
                )
            )
            return
        }

        val start = (page - 1) * PAGE_SIZE
        val end = minOf(start + PAGE_SIZE, filtered.size)
        val names = filtered.subList(start, end).joinToString(", ") { it.username }

        val headerKey = if (backendId == null) "list_header_all" else "list_header_backend"
        val lines = mutableListOf<String>()
        lines.add(
            messages.text(
                language,
                headerKey,
                mapOf(
                    "count" to filtered.size.toString(),
                    "page" to page.toString(),
                    "totalPages" to totalPages.toString(),
                    "backendId" to (backendId ?: "-"),
                ),
            )
        )
        lines.add(messages.text(language, "list_line", mapOf("players" to names)))
        if (totalPages > 1) {
            val nextCommand = buildString {
                append("/list")
                if (backendId != null) {
                    append(' ').append(backendId)
                }
                append(' ').append(page + 1)
            }
            lines.add(
                messages.text(
                    language,
                    "list_page_hint",
                    mapOf("nextPage" to (page + 1).toString(), "nextCommand" to nextCommand, "usage" to usage),
                )
            )
        }
        sender.sendMessage(lines.joinToString("\n"))
    }

    override fun suggest(context: CommandContext): List<String> {
        val args = context.args
        if (args.isEmpty()) {
            return knownBackends.sorted() + "1"
        }
        if (args.size == 1) {
            val query = args[0].trim().lowercase()
            val candidates = knownBackends.sorted() + "1"
            return if (query.isEmpty()) candidates else candidates.filter { it.lowercase().startsWith(query) }
        }
        if (args.size == 2) {
            val query = args[1].trim()
            return (1..9)
                .map { it.toString() }
                .filter { it.startsWith(query) }
        }
        return emptyList()
    }

    private fun parseQuery(args: List<String>): Query? {
        if (args.isEmpty()) {
            return Query(null, 1)
        }
        if (args.size == 1) {
            val first = args[0].trim()
            val page = first.toIntOrNull()
            return if (page != null) Query(null, page) else Query(first, 1)
        }
        if (args.size == 2) {
            val backendId = args[0].trim()
            val page = args[1].trim().toIntOrNull() ?: return null
            return Query(backendId, page)
        }
        return null
    }

    private data class Query(
        val backendId: String?,
        val page: Int,
    )

    private companion object {
        private const val PAGE_SIZE = 50
    }
}
