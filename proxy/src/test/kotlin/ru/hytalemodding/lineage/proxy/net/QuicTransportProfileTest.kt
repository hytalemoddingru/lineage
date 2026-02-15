/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class QuicTransportProfileTest {
    @Test
    fun matchesKernelBaseline() {
        assertEquals(120L, QuicTransportProfile.IDLE_TIMEOUT_VALUE)
        assertEquals(TimeUnit.SECONDS, QuicTransportProfile.IDLE_TIMEOUT_UNIT)
        assertEquals(524_288L, QuicTransportProfile.INITIAL_MAX_DATA)
        assertEquals(131_072L, QuicTransportProfile.INITIAL_MAX_STREAM_DATA_BIDI)
        assertEquals(1L, QuicTransportProfile.INITIAL_MAX_STREAMS_BIDIRECTIONAL)
        assertEquals(5_000L, QuicTransportProfile.BACKEND_CONNECT_TIMEOUT_MILLIS)
    }
}
