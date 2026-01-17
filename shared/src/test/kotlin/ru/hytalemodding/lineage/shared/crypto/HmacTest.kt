/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.crypto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HmacTest {
    @Test
    fun signAndVerifyRoundTrip() {
        val key = "super-secret".toByteArray()
        val data = "payload".toByteArray()

        val signature = Hmac.sign(key, data)

        assertTrue(Hmac.verify(key, data, signature))
    }

    @Test
    fun verifyFailsWithWrongKey() {
        val key = "super-secret".toByteArray()
        val otherKey = "other-secret".toByteArray()
        val data = "payload".toByteArray()
        val signature = Hmac.sign(key, data)

        assertFalse(Hmac.verify(otherKey, data, signature))
    }

    @Test
    fun verifyFailsWithWrongSignature() {
        val key = "super-secret".toByteArray()
        val data = "payload".toByteArray()
        val signature = Hmac.sign(key, data)
        val tampered = signature.clone().also { it[it.lastIndex] = (it.last() + 1).toByte() }

        assertFalse(Hmac.verify(key, data, tampered))
    }
}
