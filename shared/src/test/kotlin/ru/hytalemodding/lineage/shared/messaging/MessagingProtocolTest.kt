/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.messaging

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessagingProtocolTest {
    @Test
    fun handshakeRoundTrip() {
        val secret = "secret".toByteArray()
        val timestamp = 123456789L
        val nonce = ByteArray(16) { index -> index.toByte() }

        val encoded = MessagingProtocol.encodeHandshake(secret, timestamp, nonce)
        val decoded = MessagingProtocol.decode(encoded) as? HandshakePacket

        assertNotNull(decoded)
        decoded!!
        assertEquals(timestamp, decoded.timestampMillis)
        assertArrayEquals(nonce, decoded.nonce)
        assertTrue(MessagingProtocol.verifyHandshake(decoded, secret))
        assertFalse(MessagingProtocol.verifyHandshake(decoded, "other".toByteArray()))
    }

    @Test
    fun messageRoundTrip() {
        val secret = "secret".toByteArray()
        val payload = "hello".toByteArray()
        val encoded = MessagingProtocol.encodeMessage(secret, "channel.test", payload)
        val decoded = MessagingProtocol.decode(encoded) as? MessagePacket

        assertNotNull(decoded)
        decoded!!
        assertEquals("channel.test", decoded.channelId)
        assertArrayEquals(payload, decoded.payload)
        assertTrue(MessagingProtocol.verifyMessage(decoded, secret))
        assertFalse(MessagingProtocol.verifyMessage(decoded, "other".toByteArray()))
    }
}
