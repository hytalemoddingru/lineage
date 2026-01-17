/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.schedule

import ru.hytalemodding.lineage.api.schedule.Scheduler
import ru.hytalemodding.lineage.api.schedule.TaskHandle
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Basic scheduler backed by a scheduled executor.
 */
class SchedulerImpl(
    private val syncExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val asyncExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(2),
) : Scheduler {
    override fun runSync(task: Runnable): TaskHandle {
        return TaskHandleImpl(syncExecutor.submit(task))
    }

    override fun runAsync(task: Runnable): TaskHandle {
        return TaskHandleImpl(asyncExecutor.submit(task))
    }

    override fun runLater(delay: Duration, task: Runnable): TaskHandle {
        return TaskHandleImpl(syncExecutor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS))
    }

    override fun runRepeating(interval: Duration, task: Runnable): TaskHandle {
        return runRepeating(interval, interval, task)
    }

    override fun runRepeating(delay: Duration, interval: Duration, task: Runnable): TaskHandle {
        return TaskHandleImpl(
            syncExecutor.scheduleAtFixedRate(task, delay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS)
        )
    }

    fun shutdown() {
        syncExecutor.shutdown()
        asyncExecutor.shutdown()
    }
}
