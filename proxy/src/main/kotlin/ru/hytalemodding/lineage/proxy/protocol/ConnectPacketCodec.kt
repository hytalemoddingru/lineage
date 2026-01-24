/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.protocol

import io.netty.buffer.ByteBuf
import ru.hytalemodding.lineage.shared.protocol.ProtocolLimits
import ru.hytalemodding.lineage.shared.protocol.ProtocolLimitsConfig
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.min

private const val VARIABLE_BLOCK_START = ProtocolLimits.CONNECT_VARIABLE_BLOCK_START
private const val CLIENT_VERSION_LENGTH = ProtocolLimits.CLIENT_VERSION_LENGTH

/**
 * Parsed Connect packet payload from the Hytale protocol.
 */
data class ConnectPacket(
    val protocolCrc: Int,
    val protocolBuildNumber: Int,
    val clientVersion: String,
    val clientType: Byte,
    val language: String,
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
    fun decode(payload: ByteBuf, limits: ProtocolLimitsConfig = ProtocolLimitsConfig()): ConnectPacket {
        val base = payload.readerIndex()
        val limit = payload.writerIndex()
        val totalSize = limit - base
        require(totalSize >= VARIABLE_BLOCK_START) {
            "Connect payload too small: $totalSize"
        }
        val maxConnectSize = min(limits.maxConnectSize, ProtocolLimits.CONNECT_MAX_SIZE)
        require(totalSize <= maxConnectSize) {
            "Connect payload too large: $totalSize"
        }
        val nullBits = payload.getByte(base)
        val protocolCrc = payload.getIntLE(base + 1)
        val protocolBuildNumber = payload.getIntLE(base + 5)
        val clientVersion = readFixedAsciiString(payload, base + 9, CLIENT_VERSION_LENGTH)
        val clientType = payload.getByte(base + 29)
        val uuid = readUuid(payload, base + 30)

        val usernameOffset = payload.getIntLE(base + 46)
        val identityOffset = payload.getIntLE(base + 50)
        val languageOffset = payload.getIntLE(base + 54)
        val referralDataOffset = payload.getIntLE(base + 58)
        val referralSourceOffset = payload.getIntLE(base + 62)
        val varBlockStart = base + VARIABLE_BLOCK_START

        requireOffset(usernameOffset, "Username")
        val maxUsername = min(limits.maxUsernameLength, ProtocolLimits.MAX_USERNAME_LENGTH)
        val username = readVarString(
            payload,
            varBlockStart + usernameOffset,
            maxUsername,
            StandardCharsets.US_ASCII,
            "Username",
            limit,
        )
        val identityToken = if (nullBits.toInt() and 1 != 0) {
            val maxIdentity = min(limits.maxIdentityTokenLength, ProtocolLimits.MAX_IDENTITY_TOKEN_LENGTH)
            requireOffset(identityOffset, "IdentityToken")
            readVarString(
                payload,
                varBlockStart + identityOffset,
                maxIdentity,
                StandardCharsets.UTF_8,
                "IdentityToken",
                limit,
            )
        } else null
        requireOffset(languageOffset, "Language")
        val maxLanguage = min(limits.maxLanguageLength, ProtocolLimits.MAX_LANGUAGE_LENGTH)
        val language = readVarString(
            payload,
            varBlockStart + languageOffset,
            maxLanguage,
            StandardCharsets.US_ASCII,
            "Language",
            limit,
        )
        val referralData = if (nullBits.toInt() and 2 != 0) {
            val maxReferral = min(limits.maxReferralDataLength, ProtocolLimits.MAX_REFERRAL_DATA_LENGTH)
            requireOffset(referralDataOffset, "ReferralData")
            readVarBytes(
                payload,
                varBlockStart + referralDataOffset,
                maxReferral,
                "ReferralData",
                limit,
            )
        } else null
        if (nullBits.toInt() and 4 != 0) {
            requireOffset(referralSourceOffset, "ReferralSource")
            val referralPos = varBlockStart + referralSourceOffset
            val maxHost = min(limits.maxHostLength, ProtocolLimits.MAX_HOST_LENGTH)
            require(HostAddress.validateStructure(payload, referralPos, limit, maxHost)) {
                "Invalid ReferralSource structure"
            }
        }
        val referralSource = null

        return ConnectPacket(
            protocolCrc = protocolCrc,
            protocolBuildNumber = protocolBuildNumber,
            clientVersion = clientVersion,
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
        require(packet.referralData == null || packet.referralSource != null) {
            "ReferralData requires ReferralSource"
        }
        var nullBits = 0
        if (packet.identityToken != null) nullBits = nullBits or 1
        if (packet.referralData != null) nullBits = nullBits or 2
        if (packet.referralSource != null) nullBits = nullBits or 4

        out.writeByte(nullBits)
        out.writeIntLE(packet.protocolCrc)
        out.writeIntLE(packet.protocolBuildNumber)
        requireByteLength(packet.clientVersion, StandardCharsets.US_ASCII, CLIENT_VERSION_LENGTH, "ClientVersion")
        writeFixedAsciiString(out, packet.clientVersion, CLIENT_VERSION_LENGTH)
        out.writeByte(packet.clientType.toInt())
        writeUuid(out, packet.uuid)

        val slots = IntArray(5)
        for (i in 0..4) {
            slots[i] = out.writerIndex()
            out.writeIntLE(0)
        }

        val varBlockStart = out.writerIndex()

        requireByteLength(packet.username, StandardCharsets.US_ASCII, ProtocolLimits.MAX_USERNAME_LENGTH, "Username")
        out.setIntLE(slots[0], out.writerIndex() - varBlockStart)
        writeVarString(out, packet.username, StandardCharsets.US_ASCII)

        if (packet.identityToken != null) {
            requireByteLength(packet.identityToken, StandardCharsets.UTF_8, ProtocolLimits.MAX_IDENTITY_TOKEN_LENGTH, "IdentityToken")
            out.setIntLE(slots[1], out.writerIndex() - varBlockStart)
            writeVarString(out, packet.identityToken, StandardCharsets.UTF_8)
        } else out.setIntLE(slots[1], -1)

        requireByteLength(packet.language, StandardCharsets.US_ASCII, ProtocolLimits.MAX_LANGUAGE_LENGTH, "Language")
        out.setIntLE(slots[2], out.writerIndex() - varBlockStart)
        writeVarString(out, packet.language, StandardCharsets.US_ASCII)

        if (packet.referralData != null) {
            require(packet.referralData.size <= ProtocolLimits.MAX_REFERRAL_DATA_LENGTH) {
                "ReferralData exceeds max length ${ProtocolLimits.MAX_REFERRAL_DATA_LENGTH}"
            }
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

    private fun readVarString(
        buf: ByteBuf,
        offset: Int,
        maxLength: Int,
        charset: Charset,
        field: String,
        limit: Int,
    ): String {
        require(offset in 0 until limit) { "$field offset out of bounds" }
        val len = VarInt.peek(buf, offset)
        require(len >= 0) { "$field length invalid" }
        require(len <= maxLength) { "$field exceeds max length $maxLength" }
        val varIntLen = VarInt.length(buf, offset)
        require(varIntLen > 0) { "$field varint length invalid" }
        val end = offset + varIntLen + len
        require(end <= limit) { "$field exceeds payload bounds" }
        val bytes = ByteArray(len)
        buf.getBytes(offset + varIntLen, bytes)
        return String(bytes, charset)
    }

    private fun writeVarString(buf: ByteBuf, value: String, charset: Charset) {
        val bytes = value.toByteArray(charset)
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    private fun readVarBytes(
        buf: ByteBuf,
        offset: Int,
        maxLength: Int,
        field: String,
        limit: Int,
    ): ByteArray {
        require(offset in 0 until limit) { "$field offset out of bounds" }
        val len = VarInt.peek(buf, offset)
        require(len >= 0) { "$field length invalid" }
        require(len <= maxLength) { "$field exceeds max length $maxLength" }
        val varIntLen = VarInt.length(buf, offset)
        require(varIntLen > 0) { "$field varint length invalid" }
        val end = offset + varIntLen + len
        require(end <= limit) { "$field exceeds payload bounds" }
        val bytes = ByteArray(len)
        buf.getBytes(offset + varIntLen, bytes)
        return bytes
    }

    private fun requireOffset(offset: Int, field: String) {
        require(offset >= 0) { "$field offset invalid" }
    }

    private fun requireByteLength(value: String, charset: Charset, maxLength: Int, field: String) {
        val length = value.toByteArray(charset).size
        require(length <= maxLength) { "$field exceeds max length $maxLength" }
    }
}
