/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.schedule

import ru.hytalemodding.lineage.api.schedule.TaskHandle
import java.util.concurrent.Future

/**
 * Wraps a future for task cancellation.
 */
class TaskHandleImpl(
    private val future: Future<*>,
) : TaskHandle {
    override val isCancelled: Boolean
        get() = future.isCancelled

    override fun cancel() {
        future.cancel(false)
    }
}
