/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.security

import ru.hytalemodding.lineage.proxy.config.RateLimitConfig
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory fixed-window rate limiting for basic abuse protection.
 */
class RateLimitService(
    config: RateLimitConfig,
    clock: Clock = SystemClock,
) {
    val connectionPerIp = FixedWindowRateLimiter<String>(config.connectionPerIp, clock)
    val handshakePerIp = FixedWindowRateLimiter<String>(config.handshakePerIp, clock)
    val streamsPerSession = FixedWindowRateLimiter<String>(config.streamsPerSession, clock)
    val invalidPacketsPerSession = FixedWindowRateLimiter<String>(config.invalidPacketsPerSession, clock)
}

class FixedWindowRateLimiter<K>(
    private val config: ru.hytalemodding.lineage.proxy.config.RateLimitWindow,
    private val clock: Clock = SystemClock,
) {
    private val windows = ConcurrentHashMap<K, Window>()
    private val lastCleanupMillis = AtomicLong(0L)

    fun tryAcquire(key: K): Boolean {
        val now = clock.nowMillis()
        cleanupIfNeeded(now)
        val window = windows.compute(key) { _, existing ->
            val current = existing ?: Window(now, 0)
            if (now - current.windowStartMillis >= config.windowMillis) {
                current.windowStartMillis = now
                current.count = 0
            }
            current.count += 1
            current
        } ?: return false
        return window.count <= config.maxEvents
    }

    private fun cleanupIfNeeded(now: Long) {
        val last = lastCleanupMillis.get()
        if (now - last < config.windowMillis) {
            return
        }
        if (!lastCleanupMillis.compareAndSet(last, now)) {
            return
        }
        val expiry = now - config.windowMillis * 2
        windows.entries.removeIf { it.value.windowStartMillis < expiry }
    }

    private data class Window(
        var windowStartMillis: Long,
        var count: Int,
    )
}
