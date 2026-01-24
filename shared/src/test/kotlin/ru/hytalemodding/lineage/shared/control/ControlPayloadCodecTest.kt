/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.control

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ControlPayloadCodecTest {
    @Test
    fun transferRequestRoundTrip() {
        val request = TransferRequest(
            correlationId = UUID.fromString("f8d9e0c8-4c0d-4b14-9f3b-1de7d2c0c0aa"),
            playerId = UUID.fromString("2b06f5d1-4b44-4ff8-9f5e-1a25a2b7a2d2"),
            targetBackendId = "hub-1",
            referralData = byteArrayOf(1, 2, 3, 4),
        )

        val encoded = ControlPayloadCodec.encodeTransferRequest(request)
        val decoded = ControlPayloadCodec.decodeTransferRequest(encoded)

        assertNotNull(decoded)
        decoded!!
        assertEquals(request.correlationId, decoded.correlationId)
        assertEquals(request.playerId, decoded.playerId)
        assertEquals(request.targetBackendId, decoded.targetBackendId)
        assertArrayEquals(request.referralData, decoded.referralData)
    }

    @Test
    fun transferResultRoundTrip() {
        val result = TransferResult(
            correlationId = UUID.fromString("18e3116f-7f5e-4d1b-9f1f-4b2c8d3c2b11"),
            status = TransferResultStatus.FAILED,
            reason = TransferFailureReason.INTERNAL_ERROR,
        )

        val encoded = ControlPayloadCodec.encodeTransferResult(result)
        val decoded = ControlPayloadCodec.decodeTransferResult(encoded)

        assertNotNull(decoded)
        decoded!!
        assertEquals(result.correlationId, decoded.correlationId)
        assertEquals(result.status, decoded.status)
        assertEquals(result.reason, decoded.reason)
    }

    @Test
    fun tokenValidationRoundTrip() {
        val notice = TokenValidationNotice(
            playerId = UUID.fromString("5c1c2f9b-2f6e-4c30-9ef1-69dc6d4a9f6e"),
            backendId = "hub-1",
            result = TokenValidationResult.REJECTED,
            reason = TokenValidationReason.INVALID_SIGNATURE,
        )

        val encoded = ControlPayloadCodec.encodeTokenValidationNotice(notice)
        val decoded = ControlPayloadCodec.decodeTokenValidationNotice(encoded)

        assertNotNull(decoded)
        decoded!!
        assertEquals(notice.playerId, decoded.playerId)
        assertEquals(notice.backendId, decoded.backendId)
        assertEquals(notice.result, decoded.result)
        assertEquals(notice.reason, decoded.reason)
    }
}
