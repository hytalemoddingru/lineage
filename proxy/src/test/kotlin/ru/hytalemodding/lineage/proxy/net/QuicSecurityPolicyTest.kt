/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuicSecurityPolicyTest {
    @Test
    fun advertisesExpectedAlpnOrder() {
        val advertised = QuicSecurityPolicy.advertisedAlpnProtocols()
        assertArrayEquals(arrayOf("hytale/2", "hytale/1"), advertised)
    }

    @Test
    fun acceptsOnlyPrimaryNegotiatedAlpn() {
        assertTrue(QuicSecurityPolicy.isAcceptedNegotiatedAlpn("hytale/2"))
        assertFalse(QuicSecurityPolicy.isAcceptedNegotiatedAlpn("hytale/1"))
        assertFalse(QuicSecurityPolicy.isAcceptedNegotiatedAlpn("custom/1"))
    }

    @Test
    fun normalizesEmptyNegotiatedAlpnToNull() {
        assertNull(QuicSecurityPolicy.normalizeNegotiatedAlpn(""))
        assertNull(QuicSecurityPolicy.normalizeNegotiatedAlpn("   "))
    }
}
