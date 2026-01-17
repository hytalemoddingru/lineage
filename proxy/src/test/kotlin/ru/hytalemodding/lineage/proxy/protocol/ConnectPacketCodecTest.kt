/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.protocol

import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class ConnectPacketCodecTest {
    @Test
    fun encodeDecodeRoundTripWithOptionalFields() {
        val packet = ConnectPacket(
            protocolHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            clientType = 1,
            language = "en",
            identityToken = "identity-token",
            uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            username = "PlayerOne",
            referralData = "t1.payload.signature".toByteArray(StandardCharsets.UTF_8),
            referralSource = HostAddress("127.0.0.1", 25565),
        )

        val buffer = Unpooled.buffer()
        ConnectPacketCodec.encode(packet, buffer)
        buffer.readerIndex(0)

        val decoded = ConnectPacketCodec.decode(buffer)

        assertEquals(packet.protocolHash, decoded.protocolHash)
        assertEquals(packet.clientType, decoded.clientType)
        assertEquals(packet.language, decoded.language)
        assertEquals(packet.identityToken, decoded.identityToken)
        assertEquals(packet.uuid, decoded.uuid)
        assertEquals(packet.username, decoded.username)
        assertArrayEquals(packet.referralData, decoded.referralData)
        assertNull(decoded.referralSource)
    }

    @Test
    fun encodeDecodeRoundTripWithMissingOptionalFields() {
        val packet = ConnectPacket(
            protocolHash = "short-hash",
            clientType = 2,
            language = null,
            identityToken = null,
            uuid = UUID.fromString("223e4567-e89b-12d3-a456-426614174000"),
            username = "PlayerTwo",
            referralData = null,
            referralSource = null,
        )

        val buffer = Unpooled.buffer()
        ConnectPacketCodec.encode(packet, buffer)
        buffer.readerIndex(0)

        val decoded = ConnectPacketCodec.decode(buffer)

        assertEquals(packet.protocolHash, decoded.protocolHash)
        assertEquals(packet.clientType, decoded.clientType)
        assertNull(decoded.language)
        assertNull(decoded.identityToken)
        assertEquals(packet.uuid, decoded.uuid)
        assertEquals(packet.username, decoded.username)
        assertNull(decoded.referralData)
        assertNull(decoded.referralSource)
    }
}
