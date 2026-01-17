/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.event

import ru.hytalemodding.lineage.api.event.Cancellable
import ru.hytalemodding.lineage.api.event.Event
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.event.EventPriority
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe event bus implementation.
 */
class EventBusImpl : EventBus {
    private val handlers = ConcurrentHashMap<Class<out Event>, CopyOnWriteArrayList<ListenerMethod>>()
    private val listenerIndex = ConcurrentHashMap<Any, List<ListenerMethod>>()

    override fun register(listener: Any) {
        val methods = HandlerScanner.scan(listener)
        if (methods.isEmpty()) {
            return
        }
        listenerIndex[listener] = methods
        for (method in methods) {
            handlers.computeIfAbsent(method.eventType) { CopyOnWriteArrayList() }
                .add(method)
            handlers[method.eventType]?.sortWith(PRIORITY_ORDER)
        }
    }

    override fun unregister(listener: Any) {
        val methods = listenerIndex.remove(listener) ?: return
        for (method in methods) {
            handlers[method.eventType]?.remove(method)
        }
    }

    override fun post(event: Event) {
        val eventType = event.javaClass
        val targets = resolveHandlers(eventType)
        if (targets.isEmpty()) {
            return
        }
        val cancellable = event as? Cancellable
        for (handler in targets) {
            if (cancellable != null && cancellable.isCancelled && !handler.ignoreCancelled) {
                continue
            }
            handler.method.invoke(handler.listener, event)
        }
    }

    private fun resolveHandlers(eventType: Class<out Event>): List<ListenerMethod> {
        val collected = mutableListOf<ListenerMethod>()
        for ((type, list) in handlers) {
            if (type.isAssignableFrom(eventType)) {
                collected.addAll(list)
            }
        }
        if (collected.isEmpty()) {
            return emptyList()
        }
        return collected.sortedWith(PRIORITY_ORDER)
    }

    private companion object {
        private val PRIORITY_ORDER = compareBy<ListenerMethod> { priorityIndex(it.priority) }

        private fun priorityIndex(priority: EventPriority): Int {
            return when (priority) {
                EventPriority.LOWEST -> 0
                EventPriority.LOW -> 1
                EventPriority.NORMAL -> 2
                EventPriority.HIGH -> 3
                EventPriority.HIGHEST -> 4
                EventPriority.MONITOR -> 5
            }
        }
    }
}
