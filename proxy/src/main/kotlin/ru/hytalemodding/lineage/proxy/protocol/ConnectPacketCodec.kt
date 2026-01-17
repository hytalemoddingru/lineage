/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.protocol

import io.netty.buffer.ByteBuf
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val VARIABLE_BLOCK_START = 102
private const val PROTOCOL_HASH_LENGTH = 64

/**
 * Parsed Connect packet payload from the Hytale protocol.
 */
data class ConnectPacket(
    val protocolHash: String,
    val clientType: Byte,
    val language: String?,
    val identityToken: String?,
    val uuid: UUID,
    val username: String,
    val referralData: ByteArray?,
    val referralSource: HostAddress?,
)

/**
 * Packet identifier for the Connect packet.
 */
const val CONNECT_PACKET_ID = 0

/**
 * Encodes and decodes Connect packet payloads.
 */
object ConnectPacketCodec {
    /**
     * Decodes a payload [ByteBuf] into a [ConnectPacket].
     *
     * The referral source address is not decoded and will be null.
     */
    fun decode(payload: ByteBuf): ConnectPacket {
        val base = payload.readerIndex()
        val nullBits = payload.getByte(base)
        val protocolHash = readFixedAsciiString(payload, base + 1, PROTOCOL_HASH_LENGTH)
        val clientType = payload.getByte(base + 65)
        val uuid = readUuid(payload, base + 66)

        val languageOffset = payload.getIntLE(base + 82)
        val identityOffset = payload.getIntLE(base + 86)
        val usernameOffset = payload.getIntLE(base + 90)
        val referralDataOffset = payload.getIntLE(base + 94)
        val referralSourceOffset = payload.getIntLE(base + 98)
        val varBlockStart = base + VARIABLE_BLOCK_START

        val language = if (nullBits.toInt() and 1 != 0) readVarString(payload, varBlockStart + languageOffset) else null
        val identityToken = if (nullBits.toInt() and 2 != 0) readVarString(payload, varBlockStart + identityOffset) else null
        val username = readVarString(payload, varBlockStart + usernameOffset)
        val referralData = if (nullBits.toInt() and 4 != 0) readVarBytes(payload, varBlockStart + referralDataOffset) else null
        val referralSource = null

        return ConnectPacket(
            protocolHash = protocolHash,
            clientType = clientType,
            language = language,
            identityToken = identityToken,
            uuid = uuid,
            username = username,
            referralData = referralData,
            referralSource = referralSource,
        )
    }

    /**
     * Encodes a [packet] into [out].
     */
    fun encode(packet: ConnectPacket, out: ByteBuf) {
        var nullBits = 0
        if (packet.language != null) nullBits = nullBits or 1
        if (packet.identityToken != null) nullBits = nullBits or 2
        if (packet.referralData != null) nullBits = nullBits or 4
        if (packet.referralSource != null) nullBits = nullBits or 8

        out.writeByte(nullBits)
        writeFixedAsciiString(out, packet.protocolHash, PROTOCOL_HASH_LENGTH)
        out.writeByte(packet.clientType.toInt())
        writeUuid(out, packet.uuid)

        val slots = IntArray(5)
        for (i in 0..4) {
            slots[i] = out.writerIndex()
            out.writeIntLE(0)
        }

        val varBlockStart = out.writerIndex()

        if (packet.language != null) {
            out.setIntLE(slots[0], out.writerIndex() - varBlockStart)
            writeVarString(out, packet.language)
        } else out.setIntLE(slots[0], -1)

        if (packet.identityToken != null) {
            out.setIntLE(slots[1], out.writerIndex() - varBlockStart)
            writeVarString(out, packet.identityToken)
        } else out.setIntLE(slots[1], -1)

        out.setIntLE(slots[2], out.writerIndex() - varBlockStart)
        writeVarString(out, packet.username)

        if (packet.referralData != null) {
            out.setIntLE(slots[3], out.writerIndex() - varBlockStart)
            VarInt.write(out, packet.referralData.size)
            out.writeBytes(packet.referralData)
        } else out.setIntLE(slots[3], -1)

        if (packet.referralSource != null) {
            out.setIntLE(slots[4], out.writerIndex() - varBlockStart)
            packet.referralSource.serialize(out)
        } else out.setIntLE(slots[4], -1)
    }

    private fun readUuid(buf: ByteBuf, offset: Int): UUID = UUID(buf.getLong(offset), buf.getLong(offset + 8))
    private fun writeUuid(buf: ByteBuf, uuid: UUID) {
        buf.writeLong(uuid.mostSignificantBits)
        buf.writeLong(uuid.leastSignificantBits)
    }

    private fun readFixedAsciiString(buf: ByteBuf, offset: Int, length: Int): String {
        val bytes = ByteArray(length)
        buf.getBytes(offset, bytes)
        return String(bytes, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
    }

    private fun writeFixedAsciiString(buf: ByteBuf, value: String, length: Int) {
        val bytes = value.toByteArray(StandardCharsets.US_ASCII)
        buf.writeBytes(bytes.copyOf(length))
    }

    private fun readVarString(buf: ByteBuf, offset: Int): String {
        val len = VarInt.peek(buf, offset)
        val varIntLen = VarInt.length(buf, offset)
        val bytes = ByteArray(len)
        buf.getBytes(offset + varIntLen, bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun writeVarString(buf: ByteBuf, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    private fun readVarBytes(buf: ByteBuf, offset: Int): ByteArray {
        val len = VarInt.peek(buf, offset)
        val varIntLen = VarInt.length(buf, offset)
        val bytes = ByteArray(len)
        buf.getBytes(offset + varIntLen, bytes)
        return bytes
    }
}
