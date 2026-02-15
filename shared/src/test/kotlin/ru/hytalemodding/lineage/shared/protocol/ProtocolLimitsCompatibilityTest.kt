/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProtocolLimitsCompatibilityTest {
    @Test
    fun connectSchemaMatchesKernelBaseline() {
        assertEquals(46, ProtocolLimits.CONNECT_FIXED_BLOCK_SIZE)
        assertEquals(66, ProtocolLimits.CONNECT_VARIABLE_BLOCK_START)
        assertEquals(38_013, ProtocolLimits.CONNECT_MAX_SIZE)
        assertEquals(4_096, ProtocolLimits.MAX_REFERRAL_DATA_LENGTH)
    }
}
