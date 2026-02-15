/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class PlayerCommandProtocolTest {
    @Test
    fun requestRoundTrip() {
        val playerId = UUID.randomUUID()
        val payload = PlayerCommandProtocol.encodeRequest(playerId, "mod list")
        val decoded = PlayerCommandProtocol.decodeRequest(payload)

        assertNotNull(decoded)
        decoded!!
        assertEquals(playerId, decoded.playerId)
        assertEquals("mod list", decoded.command)
        assertTrue(decoded.ttlMillis > 0)
        assertEquals(16, decoded.nonce.size)
        assertEquals(PlayerCommandProtocol.VERSION, PlayerCommandProtocol.peekVersion(payload))
        assertTrue(PlayerCommandProtocol.hasSupportedVersion(payload))
    }

    @Test
    fun responseRoundTrip() {
        val playerId = UUID.randomUUID()
        val payload = PlayerCommandProtocol.encodeResponse(playerId, "ok")
        val decoded = PlayerCommandProtocol.decodeResponse(payload)

        assertNotNull(decoded)
        decoded!!
        assertEquals(playerId, decoded.playerId)
        assertEquals("ok", decoded.message)
        assertTrue(decoded.ttlMillis > 0)
        assertEquals(16, decoded.nonce.size)
    }

    @Test
    fun responseRoundTripPreservesNewlines() {
        val playerId = UUID.randomUUID()
        val payload = PlayerCommandProtocol.encodeResponse(playerId, "line1\nline2\nline3")
        val decoded = PlayerCommandProtocol.decodeResponse(payload)

        assertNotNull(decoded)
        decoded!!
        assertEquals("line1\nline2\nline3", decoded.message)
    }

    @Test
    fun rejectsInvalidPayload() {
        val invalid = "v1\nbad\npayload".toByteArray()
        assertNull(PlayerCommandProtocol.decodeRequest(invalid))
        assertNull(PlayerCommandProtocol.decodeResponse(invalid))
    }

    @Test
    fun rejectsOversizedEncodedCommand() {
        val playerId = UUID.randomUUID()
        val oversized = "x".repeat(PlayerCommandProtocol.MAX_MESSAGE_BYTES + 1)

        assertThrows(IllegalArgumentException::class.java) {
            PlayerCommandProtocol.encodeRequest(playerId, oversized)
        }
    }

    @Test
    fun rejectsOversizedPayloadOnDecode() {
        val oversized = ByteArray(PlayerCommandProtocol.MAX_PAYLOAD_BYTES + 1) { 'a'.code.toByte() }
        assertNull(PlayerCommandProtocol.decodeRequest(oversized))
        assertNull(PlayerCommandProtocol.decodeResponse(oversized))
    }

    @Test
    fun systemResponseChannelIsIsolatedFromCommandResponseChannel() {
        assertNotEquals(PlayerCommandProtocol.RESPONSE_CHANNEL_ID, PlayerCommandProtocol.SYSTEM_RESPONSE_CHANNEL_ID)
        assertNotEquals(PlayerCommandProtocol.REQUEST_CHANNEL_ID, PlayerCommandProtocol.SYSTEM_RESPONSE_CHANNEL_ID)
    }
}
