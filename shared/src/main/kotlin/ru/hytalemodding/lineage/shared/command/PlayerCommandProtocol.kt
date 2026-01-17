/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.command

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * String-based protocol for proxy player commands over messaging.
 */
object PlayerCommandProtocol {
    const val REQUEST_CHANNEL_ID = "lineage.command"
    const val RESPONSE_CHANNEL_ID = "lineage.command.response"
    private const val VERSION = "v1"

    fun encodeRequest(playerId: UUID, command: String): ByteArray {
        val sanitized = sanitize(command)
        val payload = "$VERSION\n$playerId\n$sanitized"
        return payload.toByteArray(StandardCharsets.UTF_8)
    }

    fun encodeResponse(playerId: UUID, message: String): ByteArray {
        val sanitized = sanitize(message)
        val payload = "$VERSION\n$playerId\n$sanitized"
        return payload.toByteArray(StandardCharsets.UTF_8)
    }

    fun decodeRequest(payload: ByteArray): PlayerCommandRequest? {
        val parts = split(payload) ?: return null
        val playerId = parseUuid(parts.playerId) ?: return null
        if (parts.message.isBlank()) {
            return null
        }
        return PlayerCommandRequest(playerId, parts.message)
    }

    fun decodeResponse(payload: ByteArray): PlayerCommandResponse? {
        val parts = split(payload) ?: return null
        val playerId = parseUuid(parts.playerId) ?: return null
        if (parts.message.isBlank()) {
            return null
        }
        return PlayerCommandResponse(playerId, parts.message)
    }

    private fun split(payload: ByteArray): PayloadParts? {
        val text = payload.toString(StandardCharsets.UTF_8)
        val first = text.indexOf('\n')
        if (first <= 0) {
            return null
        }
        val second = text.indexOf('\n', first + 1)
        if (second <= first) {
            return null
        }
        val version = text.substring(0, first)
        if (version != VERSION) {
            return null
        }
        val playerId = text.substring(first + 1, second).trim()
        val message = text.substring(second + 1).trim()
        if (playerId.isBlank()) {
            return null
        }
        return PayloadParts(playerId, message)
    }

    private fun parseUuid(value: String): UUID? {
        return try {
            UUID.fromString(value)
        } catch (ex: IllegalArgumentException) {
            null
        }
    }

    private fun sanitize(value: String): String {
        return value.replace('\r', ' ').replace('\n', ' ').trim()
    }

    private data class PayloadParts(
        val playerId: String,
        val message: String,
    )
}

data class PlayerCommandRequest(
    val playerId: UUID,
    val command: String,
)

data class PlayerCommandResponse(
    val playerId: UUID,
    val message: String,
)
