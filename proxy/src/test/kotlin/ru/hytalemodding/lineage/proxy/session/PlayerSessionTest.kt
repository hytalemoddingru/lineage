/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSessionTest {
    @Test
    fun allowsValidTransitions() {
        val session = PlayerSession()
        assertEquals(SessionState.NEW, session.state)

        assertTrue(session.transitionTo(SessionState.HANDSHAKING))
        assertTrue(session.transitionTo(SessionState.PLAYING))
        assertTrue(session.transitionTo(SessionState.TRANSFERRING))
        assertTrue(session.transitionTo(SessionState.HANDSHAKING))
        assertTrue(session.transitionTo(SessionState.PLAYING))
        assertTrue(session.transitionTo(SessionState.DISCONNECTED))
        assertEquals(SessionState.DISCONNECTED, session.state)
    }

    @Test
    fun rejectsInvalidTransitions() {
        val session = PlayerSession()
        assertThrows(IllegalStateException::class.java) {
            session.transitionTo(SessionState.PLAYING)
        }

        session.transitionTo(SessionState.HANDSHAKING)
        session.transitionTo(SessionState.PLAYING)
        session.transitionTo(SessionState.DISCONNECTED)

        assertThrows(IllegalStateException::class.java) {
            session.transitionTo(SessionState.HANDSHAKING)
        }
    }

    @Test
    fun ignoresNoOpTransition() {
        val session = PlayerSession()
        assertFalse(session.transitionTo(SessionState.NEW))
        assertEquals(SessionState.NEW, session.state)
    }
}
