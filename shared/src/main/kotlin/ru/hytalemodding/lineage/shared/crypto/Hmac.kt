/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for computing HMAC-SHA256 signatures.
 */
object Hmac {
    /**
     * Algorithm used for HMAC.
     */
    const val ALGORITHM = "HmacSHA256"

    /**
     * Computes HMAC-SHA256 signature for [data] using [key].
     */
    fun sign(key: ByteArray, data: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "HMAC key must not be empty" }
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        return mac.doFinal(data)
    }

    /**
     * Verifies that [expected] matches the signature for [data] with [key].
     * Uses constant-time comparison.
     */
    fun verify(key: ByteArray, data: ByteArray, expected: ByteArray): Boolean {
        if (expected.isEmpty()) {
            return false
        }
        val actual = sign(key, data)
        return constantTimeEquals(actual, expected)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) {
            return false
        }
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
