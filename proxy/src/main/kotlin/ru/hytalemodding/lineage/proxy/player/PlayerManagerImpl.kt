/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.api.player.ProxyPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe player manager implementation.
 */
class PlayerManagerImpl : PlayerManager {
    private val players = ConcurrentHashMap<UUID, ProxyPlayerImpl>()

    override fun get(id: UUID): ProxyPlayer? = players[id]

    override fun getByName(username: String): ProxyPlayer? =
        players.values.firstOrNull { it.username.equals(username, ignoreCase = true) }

    override fun all(): Collection<ProxyPlayer> = players.values

    fun getOrCreate(id: UUID, username: String): ProxyPlayerImpl {
        val existing = players[id]
        if (existing != null) {
            existing.username = username
            return existing
        }
        val created = ProxyPlayerImpl(id, username)
        players[id] = created
        return created
    }

    fun add(player: ProxyPlayerImpl) {
        players[player.id] = player
    }

    fun remove(id: UUID): ProxyPlayerImpl? {
        return players.remove(id)
    }
}
