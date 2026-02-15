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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory fixed-window rate limiting for basic abuse protection.
 */
class RateLimitService(
    config: RateLimitConfig,
    clock: Clock = SystemClock,
) {
    val handshakeInFlight = InFlightLimiter(config.handshakeConcurrentMax)
    val routingInFlight = InFlightLimiter(config.routingConcurrentMax)
    val connectionPerIp = FixedWindowRateLimiter<String>(config.connectionPerIp, clock)
    val handshakePerIp = FixedWindowRateLimiter<String>(config.handshakePerIp, clock)
    val streamsPerSession = FixedWindowRateLimiter<String>(config.streamsPerSession, clock)
    val invalidPacketsPerSession = FixedWindowRateLimiter<String>(config.invalidPacketsPerSession, clock)
}

class InFlightLimiter(private val maxInFlight: Int) {
    private val inFlight = AtomicInteger(0)

    init {
        require(maxInFlight > 0) { "maxInFlight must be > 0" }
    }

    fun tryAcquire(): Lease? {
        while (true) {
            val current = inFlight.get()
            if (current >= maxInFlight) {
                return null
            }
            if (inFlight.compareAndSet(current, current + 1)) {
                return Lease(this)
            }
        }
    }

    fun current(): Int = inFlight.get()

    private fun release() {
        inFlight.decrementAndGet()
    }

    class Lease internal constructor(private val owner: InFlightLimiter) : AutoCloseable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (released.compareAndSet(false, true)) {
                owner.release()
            }
        }
    }
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
