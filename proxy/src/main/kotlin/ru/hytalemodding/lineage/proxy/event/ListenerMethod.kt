/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.event

import ru.hytalemodding.lineage.api.event.Event
import ru.hytalemodding.lineage.api.event.EventPriority
import java.lang.reflect.Method

/**
 * Encapsulates an event handler method for dispatch.
 */
internal data class ListenerMethod(
    val eventType: Class<out Event>,
    val listener: Any,
    val method: Method,
    val priority: EventPriority,
    val ignoreCancelled: Boolean,
)
