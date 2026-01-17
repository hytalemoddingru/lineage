/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.player

import java.util.UUID

/**
 * Access to connected players and their sessions.
 */
interface PlayerManager {
    fun get(id: UUID): ProxyPlayer?
    fun getByName(username: String): ProxyPlayer?
    fun all(): Collection<ProxyPlayer>
}
