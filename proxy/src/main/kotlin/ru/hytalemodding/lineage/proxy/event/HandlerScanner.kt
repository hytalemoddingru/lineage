/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.event

import ru.hytalemodding.lineage.api.event.Event
import ru.hytalemodding.lineage.api.event.EventHandler

/**
 * Scans listener instances for methods annotated with @EventHandler.
 */
internal object HandlerScanner {
    fun scan(listener: Any): List<ListenerMethod> {
        val methods = listener.javaClass.declaredMethods
        val handlers = mutableListOf<ListenerMethod>()
        for (method in methods) {
            val annotation = method.getAnnotation(EventHandler::class.java) ?: continue
            val parameters = method.parameterTypes
            if (parameters.size != 1 || !Event::class.java.isAssignableFrom(parameters[0])) {
                continue
            }
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val eventType = parameters[0] as Class<out Event>
            handlers.add(
                ListenerMethod(
                    eventType = eventType,
                    listener = listener,
                    method = method,
                    priority = annotation.priority,
                    ignoreCancelled = annotation.ignoreCancelled,
                )
            )
        }
        return handlers
    }
}
