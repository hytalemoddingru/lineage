/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.session

import io.netty.channel.Channel
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

/**
 * Core session state machine. Holds state outside of Netty channels.
 */
class PlayerSession(
    initialState: SessionState = SessionState.NEW,
) {
    val id: UUID = UUID.randomUUID()

    private val stateRef = AtomicReference(initialState)

    val state: SessionState
        get() = stateRef.get()

    @Volatile
    var clientChannel: Channel? = null
        private set

    @Volatile
    var backendChannel: Channel? = null
        private set

    @Volatile
    var selectedBackendId: String? = null

    @Volatile
    var playerId: String? = null

    @Volatile
    var clientCertB64: String? = null

    fun attachClient(channel: Channel) {
        clientChannel = channel
    }

    fun attachBackend(channel: Channel) {
        backendChannel = channel
    }

    fun clearBackend() {
        backendChannel = null
    }

    /**
     * Attempts to transition to [next]. Returns true if state changed.
     *
     * @throws IllegalStateException if the transition is not allowed.
     */
    fun transitionTo(next: SessionState): Boolean {
        while (true) {
            val current = stateRef.get()
            if (current == next) {
                return false
            }
            if (!isAllowedTransition(current, next)) {
                throw IllegalStateException("Invalid transition: $current -> $next")
            }
            if (stateRef.compareAndSet(current, next)) {
                return true
            }
        }
    }

    private fun isAllowedTransition(from: SessionState, to: SessionState): Boolean {
        return when (from) {
            SessionState.NEW -> to == SessionState.HANDSHAKING || to == SessionState.DISCONNECTED
            SessionState.HANDSHAKING -> to == SessionState.PLAYING || to == SessionState.DISCONNECTED
            SessionState.PLAYING -> to == SessionState.TRANSFERRING || to == SessionState.DISCONNECTED
            SessionState.TRANSFERRING -> to == SessionState.HANDSHAKING || to == SessionState.DISCONNECTED
            SessionState.DISCONNECTED -> false
        }
    }
}
