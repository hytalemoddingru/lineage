/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.schedule

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SchedulerImplTest {
    private val scheduler = SchedulerImpl()

    @AfterEach
    fun tearDown() {
        scheduler.shutdown()
    }

    @Test
    fun runsDelayedTask() {
        val latch = CountDownLatch(1)
        scheduler.runLater(Duration.ofMillis(10)) {
            latch.countDown()
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun cancelsRepeatingTask() {
        val counter = AtomicInteger()
        val latch = CountDownLatch(2)
        val handle = scheduler.runRepeating(Duration.ofMillis(10)) {
            counter.incrementAndGet()
            latch.countDown()
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        handle.cancel()

        val afterCancel = counter.get()
        Thread.sleep(50)
        assertTrue(counter.get() == afterCancel)
    }
}
