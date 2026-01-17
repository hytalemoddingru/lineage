/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.schedule

import java.time.Duration

/**
 * Schedules tasks on the proxy runtime.
 */
interface Scheduler {
    fun runSync(task: Runnable): TaskHandle
    fun runAsync(task: Runnable): TaskHandle
    fun runLater(delay: Duration, task: Runnable): TaskHandle
    fun runRepeating(interval: Duration, task: Runnable): TaskHandle
    fun runRepeating(delay: Duration, interval: Duration, task: Runnable): TaskHandle
}
