/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.messaging

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.messaging.Message
import java.net.InetSocketAddress

class MessagingImplTest {
    private var server: MessagingServer? = null

    @AfterEach
    fun tearDown() {
        server?.close()
    }

    @Test
    fun dispatchesIncomingMessages() {
        val received = mutableListOf<Message>()
        val messaging = createMessaging()
        messaging.registerChannel("test") { message ->
            received.add(message)
        }

        val sender = InetSocketAddress("127.0.0.1", 12345)
        messaging.onPacket(sender, "test", "ping".toByteArray())

        assertEquals(1, received.size)
        assertEquals("test", received.first().channelId)
    }

    @Test
    fun rejectsInvalidChannelIds() {
        val messaging = createMessaging()
        assertThrows(IllegalArgumentException::class.java) {
            messaging.registerChannel(" ", {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            messaging.registerChannel("bad id", {})
        }
        val tooLong = "a".repeat(65)
        assertThrows(IllegalArgumentException::class.java) {
            messaging.registerChannel(tooLong, {})
        }
    }

    private fun createMessaging(): MessagingImpl {
        val instance = MessagingServer(
            InetSocketAddress("127.0.0.1", 0),
            "secret".toByteArray(),
        ) { _, _, _ -> }
        server = instance
        return MessagingImpl(instance)
    }
}
