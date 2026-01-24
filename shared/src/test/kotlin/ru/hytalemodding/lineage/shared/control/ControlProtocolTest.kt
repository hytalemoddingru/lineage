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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlProtocolTest {
    @Test
    fun roundTripEnvelope() {
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE) { index -> index.toByte() }
        val payload = byteArrayOf(1, 2, 3, 4)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TRANSFER_REQUEST,
            senderId = "proxy-1",
            issuedAtMillis = 1_000L,
            ttlMillis = 10_000L,
            nonce = nonce,
            payload = payload,
        )

        val encoded = ControlProtocol.encode(envelope)
        val decoded = ControlProtocol.decode(encoded)

        assertNotNull(decoded)
        decoded!!
        assertEquals(envelope.version, decoded.version)
        assertEquals(envelope.type, decoded.type)
        assertEquals(envelope.senderId, decoded.senderId)
        assertEquals(envelope.issuedAtMillis, decoded.issuedAtMillis)
        assertEquals(envelope.ttlMillis, decoded.ttlMillis)
        assertArrayEquals(envelope.nonce, decoded.nonce)
        assertArrayEquals(envelope.payload, decoded.payload)
    }

    @Test
    fun rejectsBadMagicOrVersion() {
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TOKEN_VALIDATION,
            senderId = "proxy-1",
            issuedAtMillis = 1L,
            ttlMillis = 1L,
            nonce = nonce,
            payload = byteArrayOf(0),
        )
        val encoded = ControlProtocol.encode(envelope)

        val badMagic = encoded.clone()
        badMagic[0] = 0x00
        assertNull(ControlProtocol.decode(badMagic))

        val badVersion = encoded.clone()
        badVersion[2] = (ControlProtocol.VERSION + 1).toByte()
        assertNull(ControlProtocol.decode(badVersion))
    }

    @Test
    fun rejectsInvalidPayloadLength() {
        val nonce = ByteArray(ControlProtocol.NONCE_SIZE)
        val envelope = ControlEnvelope(
            version = ControlProtocol.VERSION,
            type = ControlMessageType.TRANSFER_RESULT,
            senderId = "proxy-1",
            issuedAtMillis = 1L,
            ttlMillis = 1L,
            nonce = nonce,
            payload = byteArrayOf(1, 2, 3),
        )
        val encoded = ControlProtocol.encode(envelope)

        val tampered = encoded.clone()
        val payloadLengthOffset = tampered.size - envelope.payload.size - 4
        tampered[payloadLengthOffset] = 0x00
        tampered[payloadLengthOffset + 1] = 0x00
        tampered[payloadLengthOffset + 2] = 0x00
        tampered[payloadLengthOffset + 3] = 0x7F
        assertNull(ControlProtocol.decode(tampered))
    }

    @Test
    fun validatesTimestampsWithSkew() {
        assertTrue(ControlProtocol.isTimestampValid(1_000L, 500L, 1_000L, 0))
        assertFalse(ControlProtocol.isTimestampValid(1_000L, 500L, 1_501L, 0))
        assertTrue(ControlProtocol.isTimestampValid(1_000L, 500L, 1_600L, 100))
        assertFalse(ControlProtocol.isTimestampValid(1_000L, 0L, 1_000L, 0))
    }
}
