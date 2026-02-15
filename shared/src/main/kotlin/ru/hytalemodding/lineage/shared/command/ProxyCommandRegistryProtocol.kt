/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.command

import ru.hytalemodding.lineage.shared.control.ControlProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

object ProxyCommandRegistryProtocol {
    const val REGISTRY_CHANNEL_ID = "lineage.command.registry"
    const val REQUEST_CHANNEL_ID = "lineage.command.registry.request"
    const val DEFAULT_TTL_MILLIS = 10_000L
    const val VERSION: Byte = 2
    const val MAX_PACKET_BYTES = 65_535

    private const val MAX_COMMANDS = 1024
    private const val MAX_ALIASES = 64
    private const val MAX_STRING_BYTES = 1024
    private const val MAX_SENDER_ID_LENGTH = 64

    private val random = SecureRandom()

    fun peekVersion(payload: ByteArray): Int? {
        if (payload.isEmpty()) {
            return null
        }
        return payload[0].toInt() and 0xFF
    }

    fun hasSupportedVersion(payload: ByteArray): Boolean {
        return peekVersion(payload) == (VERSION.toInt() and 0xFF)
    }

    fun encodeSnapshot(
        commands: List<ProxyCommandDescriptor>,
        senderId: String = "proxy",
        issuedAtMillis: Long = System.currentTimeMillis(),
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
    ): ByteArray {
        require(commands.size <= MAX_COMMANDS) { "commands exceed max count $MAX_COMMANDS" }
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        writeHeader(data, senderId, issuedAtMillis, ttlMillis, nextNonce())
        data.writeInt(commands.size)
        for (command in commands) {
            require(command.aliases.size <= MAX_ALIASES) { "aliases exceed max count $MAX_ALIASES" }
            writeString(data, command.namespace)
            writeString(data, command.name)
            writeString(data, command.description)
            writeString(data, command.usage)
            writeString(data, command.permission ?: "")
            data.writeInt(command.flags)
            data.writeInt(command.aliases.size)
            for (alias in command.aliases) {
                writeString(data, alias)
            }
        }
        val bytes = out.toByteArray()
        require(bytes.size <= MAX_PACKET_BYTES) { "snapshot payload exceeds max size $MAX_PACKET_BYTES bytes" }
        return bytes
    }

    fun decodeSnapshot(payload: ByteArray): ProxyCommandRegistrySnapshot? {
        if (payload.size > MAX_PACKET_BYTES) {
            return null
        }
        val input = ByteArrayInputStream(payload)
        val data = DataInputStream(input)
        return try {
            val header = readHeader(data) ?: return null
            val count = data.readInt()
            if (count < 0 || count > MAX_COMMANDS) {
                return null
            }
            val commands = ArrayList<ProxyCommandDescriptor>(count)
            repeat(count) {
                val namespace = readString(data) ?: return null
                val name = readString(data) ?: return null
                val description = readString(data) ?: return null
                val usage = readString(data) ?: return null
                val permissionRaw = readString(data) ?: return null
                val flags = data.readInt()
                val aliasCount = data.readInt()
                if (aliasCount < 0 || aliasCount > MAX_ALIASES) {
                    return null
                }
                val aliases = ArrayList<String>(aliasCount)
                repeat(aliasCount) {
                    val alias = readString(data) ?: return null
                    aliases.add(alias)
                }
                val permission = permissionRaw.ifBlank { null }
                commands.add(
                    ProxyCommandDescriptor(
                        namespace = namespace,
                        name = name,
                        aliases = aliases,
                        description = description,
                        usage = usage,
                        permission = permission,
                        flags = flags,
                    )
                )
            }
            if (input.available() != 0) {
                return null
            }
            ProxyCommandRegistrySnapshot(
                senderId = header.senderId,
                issuedAtMillis = header.issuedAtMillis,
                ttlMillis = header.ttlMillis,
                nonce = header.nonce,
                commands = commands,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun encodeRequest(
        senderId: String = "backend",
        issuedAtMillis: Long = System.currentTimeMillis(),
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        writeHeader(data, senderId, issuedAtMillis, ttlMillis, nextNonce())
        val bytes = out.toByteArray()
        require(bytes.size <= MAX_PACKET_BYTES) { "request payload exceeds max size $MAX_PACKET_BYTES bytes" }
        return bytes
    }

    fun decodeRequest(payload: ByteArray): ProxyCommandRegistryRequest? {
        if (payload.size > MAX_PACKET_BYTES) {
            return null
        }
        val input = ByteArrayInputStream(payload)
        val data = DataInputStream(input)
        return try {
            val header = readHeader(data) ?: return null
            if (input.available() != 0) {
                return null
            }
            ProxyCommandRegistryRequest(
                senderId = header.senderId,
                issuedAtMillis = header.issuedAtMillis,
                ttlMillis = header.ttlMillis,
                nonce = header.nonce,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeHeader(
        data: DataOutputStream,
        senderId: String,
        issuedAtMillis: Long,
        ttlMillis: Long,
        nonce: ByteArray,
    ) {
        val senderIdBytes = senderId.trim().toByteArray(StandardCharsets.UTF_8)
        require(senderIdBytes.isNotEmpty()) { "senderId must not be blank" }
        require(senderIdBytes.size <= MAX_SENDER_ID_LENGTH) { "senderId exceeds max length $MAX_SENDER_ID_LENGTH" }
        require(ttlMillis > 0) { "ttlMillis must be > 0" }
        require(nonce.size == ControlProtocol.NONCE_SIZE) { "nonce must be ${ControlProtocol.NONCE_SIZE} bytes" }
        data.writeByte(VERSION.toInt())
        data.writeLong(issuedAtMillis)
        data.writeLong(ttlMillis)
        data.writeByte(senderIdBytes.size)
        data.write(senderIdBytes)
        data.write(nonce)
    }

    private fun readHeader(data: DataInputStream): Header? {
        val version = data.readUnsignedByte().toByte()
        if (version != VERSION) {
            return null
        }
        val issuedAtMillis = data.readLong()
        val ttlMillis = data.readLong()
        if (ttlMillis <= 0) {
            return null
        }
        val senderLen = data.readUnsignedByte()
        if (senderLen <= 0 || senderLen > MAX_SENDER_ID_LENGTH) {
            return null
        }
        val senderBytes = ByteArray(senderLen)
        data.readFully(senderBytes)
        val senderId = senderBytes.toString(StandardCharsets.UTF_8)
        if (senderId.isBlank()) {
            return null
        }
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE)
        data.readFully(nonce)
        return Header(senderId, issuedAtMillis, ttlMillis, nonce)
    }

    private fun writeString(data: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "Command string exceeds max size" }
        data.writeInt(bytes.size)
        data.write(bytes)
    }

    private fun readString(data: DataInputStream): String? {
        val length = data.readInt()
        if (length < 0 || length > MAX_STRING_BYTES) {
            return null
        }
        val bytes = ByteArray(length)
        data.readFully(bytes)
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun nextNonce(): ByteArray {
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE)
        random.nextBytes(nonce)
        return nonce
    }

    private data class Header(
        val senderId: String,
        val issuedAtMillis: Long,
        val ttlMillis: Long,
        val nonce: ByteArray,
    )
}

data class ProxyCommandDescriptor(
    val namespace: String,
    val name: String,
    val aliases: List<String>,
    val description: String,
    val usage: String,
    val permission: String?,
    val flags: Int,
)

data class ProxyCommandRegistrySnapshot(
    val senderId: String,
    val issuedAtMillis: Long,
    val ttlMillis: Long,
    val nonce: ByteArray,
    val commands: List<ProxyCommandDescriptor>,
)

data class ProxyCommandRegistryRequest(
    val senderId: String,
    val issuedAtMillis: Long,
    val ttlMillis: Long,
    val nonce: ByteArray,
)

object ProxyCommandFlags {
    const val PLAYER_ONLY = 1
    const val HIDDEN = 1 shl 1
}
