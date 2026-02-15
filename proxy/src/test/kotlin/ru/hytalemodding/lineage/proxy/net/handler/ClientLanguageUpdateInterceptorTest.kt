/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net.handler

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.protocol.UpdateLanguagePacketCodec
import ru.hytalemodding.lineage.proxy.protocol.VarInt
import ru.hytalemodding.lineage.proxy.session.PlayerSession
import java.nio.charset.StandardCharsets
import java.util.UUID

class ClientLanguageUpdateInterceptorTest {
    @Test
    fun updatesPlayerLanguageFromUpdateLanguagePacket() {
        val playerId = UUID.randomUUID()
        val session = PlayerSession().apply { this.playerId = playerId.toString() }
        val playerManager = PlayerManagerImpl()
        val player = playerManager.getOrCreate(playerId, "tester", "en-US")
        val channel = EmbeddedChannel(ClientLanguageUpdateInterceptor(session, playerManager))

        val frame = buildFrame("ru-RU")
        try {
            channel.writeInbound(frame.retain())
            assertEquals("ru-RU", player.language)
        } finally {
            frame.release()
            channel.finishAndReleaseAll()
        }
    }

    @Test
    fun supportsFragmentedFrames() {
        val playerId = UUID.randomUUID()
        val session = PlayerSession().apply { this.playerId = playerId.toString() }
        val playerManager = PlayerManagerImpl()
        val player = playerManager.getOrCreate(playerId, "tester", "en-US")
        val channel = EmbeddedChannel(ClientLanguageUpdateInterceptor(session, playerManager))

        val frame = buildFrame("de-DE")
        val split = 5
        val first = frame.retainedSlice(0, split)
        val second = frame.retainedSlice(split, frame.readableBytes() - split)
        try {
            channel.writeInbound(first)
            assertEquals("en-US", player.language)
            channel.writeInbound(second)
            assertEquals("de-DE", player.language)
        } finally {
            frame.release()
            channel.finishAndReleaseAll()
        }
    }

    private fun buildFrame(language: String): io.netty.buffer.ByteBuf {
        val languageBytes = language.toByteArray(StandardCharsets.UTF_8)
        val payload = Unpooled.buffer(1 + 8 + languageBytes.size)
        payload.writeByte(1)
        VarInt.write(payload, languageBytes.size)
        payload.writeBytes(languageBytes)

        val frame = Unpooled.buffer(8 + payload.readableBytes())
        frame.writeIntLE(payload.readableBytes())
        frame.writeIntLE(UpdateLanguagePacketCodec.PACKET_ID)
        frame.writeBytes(payload, payload.readerIndex(), payload.readableBytes())
        payload.release()
        return frame
    }
}
