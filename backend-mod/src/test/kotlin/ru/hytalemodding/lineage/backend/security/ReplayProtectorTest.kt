/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.shared.time.Clock

class ReplayProtectorTest {
    @Test
    fun rejectsReplayWithinWindow() {
        val clock = MutableClock(1_000L)
        val protector = ReplayProtector(
            windowMillis = 5_000L,
            maxEntries = 1_024,
            clock = clock,
        )
        val key = ReplayKey("hub", "player-1", "nonce")

        assertTrue(protector.tryRegister(key))
        assertFalse(protector.tryRegister(key))
    }

    @Test
    fun allowsSameKeyAfterWindowExpires() {
        val clock = MutableClock(1_000L)
        val protector = ReplayProtector(
            windowMillis = 5_000L,
            maxEntries = 1_024,
            clock = clock,
        )
        val key = ReplayKey("hub", "player-1", "nonce")

        assertTrue(protector.tryRegister(key))
        clock.now += 6_000L
        assertTrue(protector.tryRegister(key))
    }

    @Test
    fun replayKeyIsIsolatedByBackendAndPlayer() {
        val clock = MutableClock(1_000L)
        val protector = ReplayProtector(
            windowMillis = 5_000L,
            maxEntries = 1_024,
            clock = clock,
        )

        assertTrue(protector.tryRegister(ReplayKey("hub", "player-1", "nonce")))
        assertTrue(protector.tryRegister(ReplayKey("minigame", "player-1", "nonce")))
        assertTrue(protector.tryRegister(ReplayKey("hub", "player-2", "nonce")))
    }

    private class MutableClock(var now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
