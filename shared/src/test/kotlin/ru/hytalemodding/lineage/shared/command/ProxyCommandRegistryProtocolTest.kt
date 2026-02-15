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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProxyCommandRegistryProtocolTest {
    @Test
    fun encodesAndDecodesSnapshot() {
        val commands = listOf(
            ProxyCommandDescriptor(
                namespace = "lineage",
                name = "mod",
                aliases = listOf("mods"),
                description = "Manage mods",
                usage = "mod list",
                permission = "lineage.command.mod",
                flags = ProxyCommandFlags.PLAYER_ONLY,
            ),
            ProxyCommandDescriptor(
                namespace = "testmod",
                name = "ping",
                aliases = emptyList(),
                description = "Ping",
                usage = "ping",
                permission = null,
                flags = 0,
            ),
        )
        val payload = ProxyCommandRegistryProtocol.encodeSnapshot(commands)
        val decoded = ProxyCommandRegistryProtocol.decodeSnapshot(payload)

        assertNotNull(decoded)
        assertEquals(commands, decoded?.commands)
        assertEquals("proxy", decoded?.senderId)
        assertEquals(ProxyCommandRegistryProtocol.DEFAULT_TTL_MILLIS, decoded?.ttlMillis)
    }

    @Test
    fun rejectsInvalidVersion() {
        val payload = byteArrayOf(2)
        val decoded = ProxyCommandRegistryProtocol.decodeSnapshot(payload)
        assertNull(decoded)
    }

    @Test
    fun requestRoundTrip() {
        val payload = ProxyCommandRegistryProtocol.encodeRequest()
        val decoded = ProxyCommandRegistryProtocol.decodeRequest(payload)

        assertNotNull(decoded)
        assertEquals("backend", decoded?.senderId)
        assertEquals(ProxyCommandRegistryProtocol.DEFAULT_TTL_MILLIS, decoded?.ttlMillis)
        assertTrue(ProxyCommandRegistryProtocol.hasSupportedVersion(payload))
        assertEquals(ProxyCommandRegistryProtocol.VERSION.toInt(), ProxyCommandRegistryProtocol.peekVersion(payload))
    }

    @Test
    fun rejectsOversizedRequestPayload() {
        val oversized = ByteArray(ProxyCommandRegistryProtocol.MAX_PACKET_BYTES + 1) { 1 }
        assertNull(ProxyCommandRegistryProtocol.decodeRequest(oversized))
        assertNull(ProxyCommandRegistryProtocol.decodeSnapshot(oversized))
    }

    @Test
    fun rejectsTooManyCommandsOnEncode() {
        val commands = List(1025) { index ->
            ProxyCommandDescriptor(
                namespace = "lineage",
                name = "cmd$index",
                aliases = emptyList(),
                description = "d",
                usage = "u",
                permission = null,
                flags = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProxyCommandRegistryProtocol.encodeSnapshot(commands)
        }
    }
}
