/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

        assertEquals(commands, decoded?.commands)
    }

    @Test
    fun rejectsInvalidVersion() {
        val payload = byteArrayOf(2)
        val decoded = ProxyCommandRegistryProtocol.decodeSnapshot(payload)
        assertNull(decoded)
    }
}
