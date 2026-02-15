/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InFlightLimiterTest {
    @Test
    fun enforcesMaxInFlight() {
        val limiter = InFlightLimiter(1)

        val first = limiter.tryAcquire()
        val second = limiter.tryAcquire()

        assertNotNull(first)
        assertNull(second)
        assertEquals(1, limiter.current())
        first?.close()
        assertEquals(0, limiter.current())
    }

    @Test
    fun releaseIsIdempotent() {
        val limiter = InFlightLimiter(2)
        val lease = limiter.tryAcquire()

        assertNotNull(lease)
        assertEquals(1, limiter.current())
        lease?.close()
        lease?.close()

        assertEquals(0, limiter.current())
    }
}

