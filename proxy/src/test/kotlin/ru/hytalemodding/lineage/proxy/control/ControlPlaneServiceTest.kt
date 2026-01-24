/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.control

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.event.EventHandler
import ru.hytalemodding.lineage.api.event.player.PlayerAuthenticatedEvent
import ru.hytalemodding.lineage.api.event.security.TokenValidationEvent
import ru.hytalemodding.lineage.api.messaging.Channel
import ru.hytalemodding.lineage.api.messaging.Message
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.player.PlayerState
import ru.hytalemodding.lineage.proxy.config.MessagingConfig
import ru.hytalemodding.lineage.proxy.event.EventBusImpl
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.shared.control.ControlChannels
import ru.hytalemodding.lineage.shared.control.ControlEnvelope
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlPayloadCodec
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import ru.hytalemodding.lineage.shared.control.TokenValidationNotice
import ru.hytalemodding.lineage.shared.control.TokenValidationResult
import ru.hytalemodding.lineage.shared.time.Clock
import java.util.UUID

class ControlPlaneServiceTest {
    @Test
    fun tokenValidationFiresBeforeAuthenticatedEvent() {
        val events = mutableListOf<String>()
        val eventBus = EventBusImpl()
        val listener = object {
            @EventHandler
            fun onToken(event: TokenValidationEvent) {
                events.add("token")
            }

            @EventHandler
            fun onAuthenticated(event: PlayerAuthenticatedEvent) {
                events.add("auth")
            }
        }
        eventBus.register(listener)

        val playerId = UUID.fromString("b1c9c71d-9ad0-4a3d-9e3e-9d3d9cb2ef01")
        val players = PlayerManagerImpl()
        players.getOrCreate(playerId, "test-user")
        val messaging = TestMessaging()
        val config = MessagingConfig(
            host = "127.0.0.1",
            port = 25570,
            enabled = true,
            controlSenderId = "proxy-1",
            controlMaxPayload = 8192,
            controlReplayWindowMillis = 10_000L,
            controlReplayMaxEntries = 10_000,
            controlMaxSkewMillis = 60_000L,
            controlTtlMillis = 10_000L,
        )
        val clock = FixedClock(1_000L)
        ControlPlaneService(messaging, config, eventBus, players, clock)

        val notice = TokenValidationNotice(
            playerId = playerId,
            backendId = "backend-1",
            result = TokenValidationResult.ACCEPTED,
            reason = null,
        )
        val payload = ControlPayloadCodec.encodeTokenValidationNotice(notice)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TOKEN_VALIDATION,
            senderId = "backend-1",
            issuedAtMillis = clock.nowMillis(),
            ttlMillis = config.controlTtlMillis,
            nonce = ByteArray(ControlProtocol.NONCE_SIZE),
            payload = payload,
        )
        val packet = ControlProtocol.encode(envelope)
        messaging.deliver(ControlChannels.CONTROL_CHANNEL_ID, packet)

        assertEquals(listOf("token", "auth"), events)
        assertEquals(PlayerState.AUTHENTICATED, players.get(playerId)?.state)
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    private class TestMessaging : Messaging {
        private val handlers = mutableMapOf<String, MessageHandler>()

        override fun registerChannel(id: String, handler: MessageHandler): Channel {
            handlers[id] = handler
            return TestChannel(id)
        }

        override fun unregisterChannel(id: String) {
            handlers.remove(id)
        }

        override fun channel(id: String): Channel? {
            return handlers[id]?.let { TestChannel(id) }
        }

        fun deliver(id: String, payload: ByteArray) {
            handlers[id]?.onMessage(Message(id, payload))
        }
    }

    private class TestChannel(
        override val id: String,
    ) : Channel {
        override fun send(payload: ByteArray) {
        }
    }
}
