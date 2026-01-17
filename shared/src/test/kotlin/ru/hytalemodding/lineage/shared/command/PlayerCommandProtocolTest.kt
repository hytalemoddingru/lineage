/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    }

    @Test
    fun rejectsInvalidPayload() {
        val invalid = "v2\nbad\npayload".toByteArray()
        assertNull(PlayerCommandProtocol.decodeRequest(invalid))
        assertNull(PlayerCommandProtocol.decodeResponse(invalid))
    }
}
