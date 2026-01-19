/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.security

import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class ReplayKey(
    val backendId: String,
    val playerId: String,
    val nonce: String,
)

/**
 * Time-window replay protection with best-effort cleanup.
 */
class ReplayProtector(
    private val windowMillis: Long,
    private val maxEntries: Int,
    private val clock: Clock = SystemClock,
) {
    private val entries = ConcurrentHashMap<ReplayKey, ReplayEntry>()
    private val lastCleanupMillis = AtomicLong(0L)

    fun tryRegister(key: ReplayKey): Boolean {
        val now = clock.nowMillis()
        cleanupIfNeeded(now)
        val existing = entries[key]
        if (existing != null && existing.expiresAtMillis > now) {
            existing.lastSeenMillis = now
            return false
        }
        entries[key] = ReplayEntry(now + windowMillis, now)
        trimToSize()
        return true
    }

    private fun cleanupIfNeeded(now: Long) {
        val last = lastCleanupMillis.get()
        if (now - last < windowMillis) {
            return
        }
        if (!lastCleanupMillis.compareAndSet(last, now)) {
            return
        }
        entries.entries.removeIf { it.value.expiresAtMillis <= now }
    }

    private fun trimToSize() {
        if (entries.size <= maxEntries) {
            return
        }
        val overflow = entries.size - maxEntries
        val victims = entries.entries
            .sortedBy { it.value.lastSeenMillis }
            .take(overflow)
        for (entry in victims) {
            entries.remove(entry.key)
        }
    }

    private data class ReplayEntry(
        val expiresAtMillis: Long,
        var lastSeenMillis: Long,
    )
}
