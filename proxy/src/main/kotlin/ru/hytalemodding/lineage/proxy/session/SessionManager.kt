/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.session

import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Thread-safe registry for active player sessions.
 */
class SessionManager {
    private val sessions = ConcurrentHashMap<UUID, PlayerSession>()

    /**
     * Creates and registers a new [PlayerSession].
     */
    fun create(): PlayerSession {
        val session = PlayerSession()
        sessions[session.id] = session
        return session
    }

    /**
     * Removes a session from the registry.
     */
    fun remove(session: PlayerSession) {
        sessions.remove(session.id)
    }

    /**
     * Returns number of currently tracked sessions.
     */
    fun size(): Int = sessions.size
}
