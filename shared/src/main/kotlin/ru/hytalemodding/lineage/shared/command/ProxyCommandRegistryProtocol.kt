/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.command

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

object ProxyCommandRegistryProtocol {
    const val REGISTRY_CHANNEL_ID = "lineage.command.registry"
    const val REQUEST_CHANNEL_ID = "lineage.command.registry.request"

    private const val VERSION: Byte = 1
    private const val MAX_COMMANDS = 1024
    private const val MAX_ALIASES = 64
    private const val MAX_STRING_BYTES = 1024

    fun encodeSnapshot(commands: List<ProxyCommandDescriptor>): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.writeByte(VERSION.toInt())
        data.writeInt(commands.size)
        for (command in commands) {
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
        return out.toByteArray()
    }

    fun decodeSnapshot(payload: ByteArray): ProxyCommandRegistrySnapshot? {
        val input = ByteArrayInputStream(payload)
        val data = DataInputStream(input)
        return try {
            val version = data.readUnsignedByte().toByte()
            if (version != VERSION) {
                return null
            }
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
            ProxyCommandRegistrySnapshot(commands)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeRequest(): ByteArray {
        return byteArrayOf(VERSION)
    }

    fun decodeRequest(payload: ByteArray): Boolean {
        return payload.size == 1 && payload[0] == VERSION
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
    val commands: List<ProxyCommandDescriptor>,
)

object ProxyCommandFlags {
    const val PLAYER_ONLY = 1
    const val HIDDEN = 1 shl 1
}
