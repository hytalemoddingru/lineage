/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.control

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.shared.time.Clock

class ControlReplayProtectorTest {
    @Test
    fun rejectsReplayWithinWindow() {
        val clock = MutableClock(1_000L)
        val protector = ControlReplayProtector(
            windowMillis = 5_000L,
            maxEntries = 1024,
            clock = clock,
        )
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE) { 1 }

        assertTrue(protector.tryRegister("proxy", ControlMessageType.TRANSFER_REQUEST, nonce))
        assertFalse(protector.tryRegister("proxy", ControlMessageType.TRANSFER_REQUEST, nonce))
    }

    @Test
    fun allowsSameNonceAfterWindowExpires() {
        val clock = MutableClock(1_000L)
        val protector = ControlReplayProtector(
            windowMillis = 5_000L,
            maxEntries = 1024,
            clock = clock,
        )
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE) { 2 }

        assertTrue(protector.tryRegister("proxy", ControlMessageType.TRANSFER_REQUEST, nonce))
        clock.now += 6_000L
        assertTrue(protector.tryRegister("proxy", ControlMessageType.TRANSFER_REQUEST, nonce))
    }

    @Test
    fun replayKeyIsIsolatedBySenderAndType() {
        val clock = MutableClock(1_000L)
        val protector = ControlReplayProtector(
            windowMillis = 5_000L,
            maxEntries = 1024,
            clock = clock,
        )
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE) { 3 }

        assertTrue(protector.tryRegister("proxy", ControlMessageType.TRANSFER_REQUEST, nonce))
        assertTrue(protector.tryRegister("backend-1", ControlMessageType.TRANSFER_REQUEST, nonce))
        assertTrue(protector.tryRegister("proxy", ControlMessageType.TRANSFER_RESULT, nonce))
    }

    private class MutableClock(var now: Long) : Clock {
        override fun nowMillis(): Long = now
    }
}
