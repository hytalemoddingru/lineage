/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks short-lived backend outages to avoid repeated long connection hangs.
 */
class BackendAvailabilityTracker(
    private val knownBackendIds: Set<String> = emptySet(),
    private val unavailableCooldownMillis: Long = 30_000L,
    private val onlineHeartbeatTimeoutMillis: Long = 15_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val unavailableUntil = ConcurrentHashMap<String, Long>()
    private val forcedOffline = ConcurrentHashMap.newKeySet<String>()
    private val onlineUntil = ConcurrentHashMap<String, Long>()
    private val seenStatus = ConcurrentHashMap.newKeySet<String>()

    fun markUnavailable(backendId: String) {
        unavailableUntil[backendId] = nowMillis() + unavailableCooldownMillis
    }

    fun markAvailable(backendId: String) {
        unavailableUntil.remove(backendId)
        forcedOffline.remove(backendId)
        onlineUntil[backendId] = nowMillis() + onlineHeartbeatTimeoutMillis
        seenStatus.add(backendId)
    }

    fun markReportedOffline(backendId: String) {
        forcedOffline.add(backendId)
        onlineUntil.remove(backendId)
        seenStatus.add(backendId)
    }

    fun markReportedOnline(backendId: String) {
        forcedOffline.remove(backendId)
        unavailableUntil.remove(backendId)
        onlineUntil[backendId] = nowMillis() + onlineHeartbeatTimeoutMillis
        seenStatus.add(backendId)
    }

    fun status(backendId: String): BackendAvailabilityStatus {
        if (backendId in forcedOffline) {
            return BackendAvailabilityStatus.OFFLINE
        }
        val unavailable = unavailableUntil[backendId]
        if (unavailable != null) {
            if (unavailable > nowMillis()) {
                return BackendAvailabilityStatus.OFFLINE
            }
            unavailableUntil.remove(backendId, unavailable)
        }
        val online = onlineUntil[backendId]
        if (online != null) {
            if (online > nowMillis()) {
                return BackendAvailabilityStatus.ONLINE
            }
            onlineUntil.remove(backendId, online)
            seenStatus.add(backendId)
            return BackendAvailabilityStatus.OFFLINE
        }
        if (backendId in seenStatus) {
            return BackendAvailabilityStatus.OFFLINE
        }
        if (knownBackendIds.isNotEmpty() && backendId !in knownBackendIds) {
            return BackendAvailabilityStatus.UNKNOWN
        }
        return BackendAvailabilityStatus.UNKNOWN
    }

    fun isTemporarilyUnavailable(backendId: String): Boolean {
        return status(backendId) == BackendAvailabilityStatus.OFFLINE
    }
}

enum class BackendAvailabilityStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN,
}
