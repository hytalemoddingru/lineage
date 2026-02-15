/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.control

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

object ControlPayloadCodec {
    private const val RESULT_OK: Byte = 1
    private const val RESULT_FAILED: Byte = 2

    private const val FAILURE_BACKEND_NOT_FOUND: Byte = 1
    private const val FAILURE_REFERRAL_REJECTED: Byte = 2
    private const val FAILURE_INTERNAL_ERROR: Byte = 3

    private const val TOKEN_ACCEPTED: Byte = 1
    private const val TOKEN_REJECTED: Byte = 2

    private const val TOKEN_REASON_MALFORMED: Byte = 1
    private const val TOKEN_REASON_INVALID_SIGNATURE: Byte = 2
    private const val TOKEN_REASON_EXPIRED: Byte = 3
    private const val TOKEN_REASON_NOT_YET_VALID: Byte = 4
    private const val TOKEN_REASON_UNSUPPORTED_VERSION: Byte = 5
    private const val TOKEN_REASON_TARGET_MISMATCH: Byte = 6
    private const val TOKEN_REASON_REPLAYED: Byte = 7
    private const val BACKEND_ONLINE: Byte = 1
    private const val BACKEND_OFFLINE: Byte = 2

    fun encodeTransferRequest(request: TransferRequest): ByteArray {
        val backendBytes = request.targetBackendId.toByteArray(StandardCharsets.UTF_8)
        val payloadSize = 16 + 16 + 4 + backendBytes.size + 4 + request.referralData.size
        val buffer = ByteBuffer.allocate(payloadSize)
        writeUuid(buffer, request.correlationId)
        writeUuid(buffer, request.playerId)
        writeBytes(buffer, backendBytes)
        writeBytes(buffer, request.referralData)
        return buffer.array()
    }

    fun decodeTransferRequest(payload: ByteArray): TransferRequest? {
        val buffer = ByteBuffer.wrap(payload)
        val correlationId = readUuid(buffer) ?: return null
        val playerId = readUuid(buffer) ?: return null
        val backendBytes = readBytes(buffer) ?: return null
        val referralData = readBytes(buffer) ?: return null
        if (buffer.hasRemaining()) {
            return null
        }
        val backendId = backendBytes.toString(StandardCharsets.UTF_8)
        if (backendId.isBlank()) {
            return null
        }
        return TransferRequest(correlationId, playerId, backendId, referralData)
    }

    fun encodeTransferResult(result: TransferResult): ByteArray {
        val buffer = ByteBuffer.allocate(16 + 1 + 1)
        writeUuid(buffer, result.correlationId)
        buffer.put(encodeResult(result.status))
        buffer.put(encodeFailureReason(result.reason))
        return buffer.array()
    }

    fun decodeTransferResult(payload: ByteArray): TransferResult? {
        val buffer = ByteBuffer.wrap(payload)
        val correlationId = readUuid(buffer) ?: return null
        if (buffer.remaining() != 2) {
            return null
        }
        val status = decodeResult(buffer.get()) ?: return null
        val reason = decodeFailureReason(buffer.get())
        return TransferResult(correlationId, status, reason)
    }

    fun encodeTokenValidationNotice(notice: TokenValidationNotice): ByteArray {
        val backendBytes = notice.backendId.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(16 + 4 + backendBytes.size + 1 + 1)
        writeUuid(buffer, notice.playerId)
        writeBytes(buffer, backendBytes)
        buffer.put(encodeTokenResult(notice.result))
        buffer.put(encodeTokenReason(notice.reason))
        return buffer.array()
    }

    fun decodeTokenValidationNotice(payload: ByteArray): TokenValidationNotice? {
        val buffer = ByteBuffer.wrap(payload)
        val playerId = readUuid(buffer) ?: return null
        val backendBytes = readBytes(buffer) ?: return null
        if (buffer.remaining() != 2) {
            return null
        }
        val result = decodeTokenResult(buffer.get()) ?: return null
        val reason = decodeTokenReason(buffer.get())
        val backendId = backendBytes.toString(StandardCharsets.UTF_8)
        if (backendId.isBlank()) {
            return null
        }
        return TokenValidationNotice(playerId, backendId, result, reason)
    }

    fun encodeBackendStatusNotice(notice: BackendStatusNotice): ByteArray {
        val backendBytes = notice.backendId.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + backendBytes.size + 1)
        writeBytes(buffer, backendBytes)
        buffer.put(if (notice.online) BACKEND_ONLINE else BACKEND_OFFLINE)
        return buffer.array()
    }

    fun decodeBackendStatusNotice(payload: ByteArray): BackendStatusNotice? {
        val buffer = ByteBuffer.wrap(payload)
        val backendBytes = readBytes(buffer) ?: return null
        if (buffer.remaining() != 1) {
            return null
        }
        val online = when (buffer.get()) {
            BACKEND_ONLINE -> true
            BACKEND_OFFLINE -> false
            else -> return null
        }
        val backendId = backendBytes.toString(StandardCharsets.UTF_8)
        if (backendId.isBlank()) {
            return null
        }
        return BackendStatusNotice(backendId, online)
    }

    private fun writeUuid(buffer: ByteBuffer, uuid: UUID) {
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
    }

    private fun readUuid(buffer: ByteBuffer): UUID? {
        if (buffer.remaining() < 16) {
            return null
        }
        val most = buffer.long
        val least = buffer.long
        return UUID(most, least)
    }

    private fun writeBytes(buffer: ByteBuffer, bytes: ByteArray) {
        buffer.putInt(bytes.size)
        buffer.put(bytes)
    }

    private fun readBytes(buffer: ByteBuffer): ByteArray? {
        if (buffer.remaining() < 4) {
            return null
        }
        val length = buffer.int
        if (length < 0 || buffer.remaining() < length) {
            return null
        }
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return bytes
    }

    private fun encodeResult(status: TransferResultStatus): Byte {
        return when (status) {
            TransferResultStatus.OK -> RESULT_OK
            TransferResultStatus.FAILED -> RESULT_FAILED
        }
    }

    private fun decodeResult(value: Byte): TransferResultStatus? {
        return when (value) {
            RESULT_OK -> TransferResultStatus.OK
            RESULT_FAILED -> TransferResultStatus.FAILED
            else -> null
        }
    }

    private fun encodeFailureReason(reason: TransferFailureReason?): Byte {
        return when (reason) {
            null -> 0
            TransferFailureReason.BACKEND_NOT_FOUND -> FAILURE_BACKEND_NOT_FOUND
            TransferFailureReason.REFERRAL_REJECTED -> FAILURE_REFERRAL_REJECTED
            TransferFailureReason.INTERNAL_ERROR -> FAILURE_INTERNAL_ERROR
        }
    }

    private fun decodeFailureReason(value: Byte): TransferFailureReason? {
        return when (value) {
            0.toByte() -> null
            FAILURE_BACKEND_NOT_FOUND -> TransferFailureReason.BACKEND_NOT_FOUND
            FAILURE_REFERRAL_REJECTED -> TransferFailureReason.REFERRAL_REJECTED
            FAILURE_INTERNAL_ERROR -> TransferFailureReason.INTERNAL_ERROR
            else -> null
        }
    }

    private fun encodeTokenResult(result: TokenValidationResult): Byte {
        return when (result) {
            TokenValidationResult.ACCEPTED -> TOKEN_ACCEPTED
            TokenValidationResult.REJECTED -> TOKEN_REJECTED
        }
    }

    private fun decodeTokenResult(value: Byte): TokenValidationResult? {
        return when (value) {
            TOKEN_ACCEPTED -> TokenValidationResult.ACCEPTED
            TOKEN_REJECTED -> TokenValidationResult.REJECTED
            else -> null
        }
    }

    private fun encodeTokenReason(reason: TokenValidationReason?): Byte {
        return when (reason) {
            null -> 0
            TokenValidationReason.MALFORMED -> TOKEN_REASON_MALFORMED
            TokenValidationReason.INVALID_SIGNATURE -> TOKEN_REASON_INVALID_SIGNATURE
            TokenValidationReason.EXPIRED -> TOKEN_REASON_EXPIRED
            TokenValidationReason.NOT_YET_VALID -> TOKEN_REASON_NOT_YET_VALID
            TokenValidationReason.UNSUPPORTED_VERSION -> TOKEN_REASON_UNSUPPORTED_VERSION
            TokenValidationReason.TARGET_MISMATCH -> TOKEN_REASON_TARGET_MISMATCH
            TokenValidationReason.REPLAYED -> TOKEN_REASON_REPLAYED
        }
    }

    private fun decodeTokenReason(value: Byte): TokenValidationReason? {
        return when (value) {
            0.toByte() -> null
            TOKEN_REASON_MALFORMED -> TokenValidationReason.MALFORMED
            TOKEN_REASON_INVALID_SIGNATURE -> TokenValidationReason.INVALID_SIGNATURE
            TOKEN_REASON_EXPIRED -> TokenValidationReason.EXPIRED
            TOKEN_REASON_NOT_YET_VALID -> TokenValidationReason.NOT_YET_VALID
            TOKEN_REASON_UNSUPPORTED_VERSION -> TokenValidationReason.UNSUPPORTED_VERSION
            TOKEN_REASON_TARGET_MISMATCH -> TokenValidationReason.TARGET_MISMATCH
            TOKEN_REASON_REPLAYED -> TokenValidationReason.REPLAYED
            else -> null
        }
    }
}
