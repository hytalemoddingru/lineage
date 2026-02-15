/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class BackendAvailabilityTrackerTest {
    @Test
    fun marksBackendUnavailableForCooldownWindow() {
        val now = AtomicLong(1_000L)
        val tracker = BackendAvailabilityTracker(unavailableCooldownMillis = 5_000L) { now.get() }

        tracker.markUnavailable("survival")
        assertTrue(tracker.isTemporarilyUnavailable("survival"))

        now.set(7_000L)
        assertFalse(tracker.isTemporarilyUnavailable("survival"))
    }

    @Test
    fun clearsBackendOnSuccessfulRecovery() {
        val tracker = BackendAvailabilityTracker()
        tracker.markUnavailable("hub")
        assertTrue(tracker.isTemporarilyUnavailable("hub"))

        tracker.markAvailable("hub")
        assertFalse(tracker.isTemporarilyUnavailable("hub"))
    }

    @Test
    fun keepsBackendOfflineUntilReportedOnline() {
        val tracker = BackendAvailabilityTracker()

        tracker.markReportedOffline("survival")
        assertTrue(tracker.isTemporarilyUnavailable("survival"))

        tracker.markReportedOnline("survival")
        assertFalse(tracker.isTemporarilyUnavailable("survival"))
    }

    @Test
    fun reportsUnknownWhenNoStatusWasObservedYet() {
        val tracker = BackendAvailabilityTracker(knownBackendIds = setOf("hub"))
        assertEquals(BackendAvailabilityStatus.UNKNOWN, tracker.status("hub"))
    }

    @Test
    fun reportsOfflineWhenOnlineHeartbeatExpires() {
        val now = AtomicLong(1_000L)
        val tracker = BackendAvailabilityTracker(
            knownBackendIds = setOf("hub"),
            onlineHeartbeatTimeoutMillis = 2_000L,
            nowMillis = { now.get() },
        )

        tracker.markReportedOnline("hub")
        assertEquals(BackendAvailabilityStatus.ONLINE, tracker.status("hub"))

        now.set(3_500L)
        assertEquals(BackendAvailabilityStatus.OFFLINE, tracker.status("hub"))
    }
}
