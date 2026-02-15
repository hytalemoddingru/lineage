/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.shared.command.ProxyCommandDescriptor
import ru.hytalemodding.lineage.shared.command.ProxyCommandFlags
import ru.hytalemodding.lineage.shared.command.ProxyCommandRegistryProtocol
import ru.hytalemodding.lineage.shared.time.Clock

class ProxyCommandBridgeTest {
    @Test
    fun rejectsSnapshotFromUnexpectedSender() {
        val messaging = FakeMessaging()
        val registrar = FakeCommandRegistrar()
        val bridge = ProxyCommandBridge(
            isMessagingEnabled = { true },
            commandRegistrar = registrar,
            messaging = messaging,
            clock = FixedClock(2_000L),
        )
        bridge.start()

        try {
            messaging.emit(
                ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID,
                snapshotPayload(
                    senderId = "attacker",
                    issuedAt = 1_000L,
                    ttl = 10_000L,
                    commands = listOf(sampleDescriptor()),
                ),
            )

            assertTrue(registrar.registeredDefinitions.isEmpty())
            assertFalse(bridge.isSynchronizedForTests())
        } finally {
            bridge.stop()
        }
    }

    @Test
    fun retriesRequestSyncUntilSnapshotArrives() {
        val messaging = FakeMessaging()
        val registrar = FakeCommandRegistrar()
        val bridge = ProxyCommandBridge(
            isMessagingEnabled = { true },
            commandRegistrar = registrar,
            messaging = messaging,
            clock = FixedClock(2_000L),
            syncRetryIntervalMillis = 25L,
        )
        bridge.start()
        try {
            waitUntil(timeoutMillis = 400L) {
                messaging.sentPackets.count { it.first == ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID } >= 2
            }
            assertFalse(bridge.isSynchronizedForTests())

            messaging.emit(
                ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID,
                snapshotPayload(
                    senderId = "proxy",
                    issuedAt = 1_000L,
                    ttl = 10_000L,
                    commands = listOf(sampleDescriptor()),
                ),
            )
            assertTrue(bridge.isSynchronizedForTests())

            val sentAfterSync = messaging.sentPackets.count { it.first == ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID }
            Thread.sleep(100L)
            val sentAfterWait = messaging.sentPackets.count { it.first == ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID }
            assertEquals(sentAfterSync, sentAfterWait)
        } finally {
            bridge.stop()
        }
    }

    @Test
    fun rejectsExpiredSnapshotDeterministically() {
        val messaging = FakeMessaging()
        val registrar = FakeCommandRegistrar()
        val bridge = ProxyCommandBridge(
            isMessagingEnabled = { true },
            commandRegistrar = registrar,
            messaging = messaging,
            clock = FixedClock(500_000L),
        )
        bridge.start()

        try {
            messaging.emit(
                ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID,
                snapshotPayload(
                    senderId = "proxy",
                    issuedAt = 1_000L,
                    ttl = 1_000L,
                    commands = listOf(sampleDescriptor()),
                ),
            )

            assertTrue(registrar.registeredDefinitions.isEmpty())
            assertFalse(bridge.isSynchronizedForTests())
        } finally {
            bridge.stop()
        }
    }

    @Test
    fun rejectsReplayedSnapshot() {
        val messaging = FakeMessaging()
        val registrar = FakeCommandRegistrar()
        val bridge = ProxyCommandBridge(
            isMessagingEnabled = { true },
            commandRegistrar = registrar,
            messaging = messaging,
            clock = FixedClock(2_000L),
        )
        bridge.start()
        val payload = snapshotPayload(
            senderId = "proxy",
            issuedAt = 1_000L,
            ttl = 10_000L,
            commands = listOf(sampleDescriptor()),
        )

        try {
            messaging.emit(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID, payload)
            messaging.emit(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID, payload)

            assertEquals(2, registrar.registeredDefinitions.size)
            assertTrue(bridge.isSynchronizedForTests())
        } finally {
            bridge.stop()
        }
    }

    @Test
    fun requestSyncSendsRequestAndResetsSynchronizationState() {
        val messaging = FakeMessaging()
        val registrar = FakeCommandRegistrar()
        val bridge = ProxyCommandBridge(
            isMessagingEnabled = { true },
            commandRegistrar = registrar,
            messaging = messaging,
            clock = FixedClock(2_000L),
        )
        bridge.start()
        try {
            messaging.emit(
                ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID,
                snapshotPayload(
                    senderId = "proxy",
                    issuedAt = 1_000L,
                    ttl = 10_000L,
                    commands = listOf(sampleDescriptor()),
                ),
            )
            assertTrue(bridge.isSynchronizedForTests())

            bridge.requestSync()

            val sent = messaging.sentPackets.lastOrNull()
            assertNotNull(sent)
            assertEquals(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, sent!!.first)
            assertNotNull(ProxyCommandRegistryProtocol.decodeRequest(sent.second))
            assertFalse(bridge.isSynchronizedForTests())
        } finally {
            bridge.stop()
        }
    }

    @Test
    fun stopUnregistersRegisteredCommands() {
        val messaging = FakeMessaging()
        val registrar = FakeCommandRegistrar()
        val bridge = ProxyCommandBridge(
            isMessagingEnabled = { true },
            commandRegistrar = registrar,
            messaging = messaging,
            clock = FixedClock(2_000L),
        )
        bridge.start()
        try {
            messaging.emit(
                ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID,
                snapshotPayload(
                    senderId = "proxy",
                    issuedAt = 1_000L,
                    ttl = 10_000L,
                    commands = listOf(sampleDescriptor(flags = ProxyCommandFlags.HIDDEN)),
                ),
            )
            assertEquals(1, registrar.registeredDefinitions.size)

            bridge.stop()

            assertEquals(1, registrar.unregisterCalls)
            assertFalse(bridge.isSynchronizedForTests())
            assertFalse(messaging.handlers.containsKey(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID))
        } finally {
            bridge.stop()
        }
    }

    private fun snapshotPayload(
        senderId: String,
        issuedAt: Long,
        ttl: Long,
        commands: List<ProxyCommandDescriptor>,
    ): ByteArray {
        return ProxyCommandRegistryProtocol.encodeSnapshot(
            commands = commands,
            senderId = senderId,
            issuedAtMillis = issuedAt,
            ttlMillis = ttl,
        )
    }

    private fun sampleDescriptor(flags: Int = 0): ProxyCommandDescriptor {
        return ProxyCommandDescriptor(
            namespace = "core",
            name = "ping",
            aliases = emptyList(),
            description = "Ping command",
            usage = "ping",
            permission = null,
            flags = flags,
        )
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    private fun waitUntil(timeoutMillis: Long, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) {
                return
            }
            Thread.sleep(10L)
        }
        assertTrue(predicate())
    }

    private class FakeMessaging : BackendBridgeMessaging {
        val handlers = linkedMapOf<String, (ByteArray) -> Unit>()
        val sentPackets = mutableListOf<Pair<String, ByteArray>>()

        override fun registerChannel(id: String, handler: (ByteArray) -> Unit) {
            handlers[id] = handler
        }

        override fun unregisterChannel(id: String) {
            handlers.remove(id)
        }

        override fun send(channelId: String, payload: ByteArray) {
            sentPackets.add(channelId to payload)
        }

        fun emit(channelId: String, payload: ByteArray) {
            handlers[channelId]?.invoke(payload)
        }
    }

    private class FakeCommandRegistrar : BackendBridgeCommandRegistrar {
        val registeredDefinitions = mutableListOf<BackendBridgeCommandDefinition>()
        var unregisterCalls = 0
        private val registeredNames = HashSet<String>()

        override fun isNameAvailable(name: String): Boolean {
            return !registeredNames.contains(name.lowercase())
        }

        override fun register(definition: BackendBridgeCommandDefinition): BackendBridgeRegistration {
            registeredDefinitions.add(definition)
            registeredNames.add(definition.name.lowercase())
            return BackendBridgeRegistration {
                unregisterCalls += 1
                registeredNames.remove(definition.name.lowercase())
            }
        }
    }
}
