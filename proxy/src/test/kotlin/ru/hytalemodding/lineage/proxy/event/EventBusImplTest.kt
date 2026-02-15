/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.event.Cancellable
import ru.hytalemodding.lineage.api.event.Event
import ru.hytalemodding.lineage.api.event.EventHandler
import ru.hytalemodding.lineage.api.event.EventPriority

class EventBusImplTest {
    @Test
    fun invokesHandlersInPriorityOrder() {
        val bus = EventBusImpl()
        val order = mutableListOf<String>()
        val low = LowListener(order)
        val high = HighListener(order)

        bus.register(high)
        bus.register(low)
        bus.post(SimpleEvent())

        assertEquals(listOf("low", "high"), order)
    }

    @Test
    fun respectsCancellation() {
        val bus = EventBusImpl()
        val order = mutableListOf<String>()
        val cancel = CancelListener(order)
        val ignored = HighCancelledListener(order)
        val monitor = IgnoreCancelledListener(order)

        bus.register(cancel)
        bus.register(ignored)
        bus.register(monitor)
        bus.post(CancellableEvent())

        assertEquals(listOf("cancel", "monitor"), order)
    }

    @Test
    fun isolatesHandlerFailureAndContinuesDispatch() {
        val bus = EventBusImpl()
        val order = mutableListOf<String>()
        val broken = BrokenListener(order)
        val high = HighListener(order)

        bus.register(broken)
        bus.register(high)
        bus.post(SimpleEvent())

        assertEquals(listOf("broken", "high"), order)
    }

    private class SimpleEvent : Event

    private class CancellableEvent : Event, Cancellable {
        override var isCancelled: Boolean = false
    }

    private class LowListener(
        private val order: MutableList<String>,
    ) {
        @EventHandler(priority = EventPriority.LOW)
        fun onEvent(event: SimpleEvent) {
            order.add("low")
        }
    }

    private class HighListener(
        private val order: MutableList<String>,
    ) {
        @EventHandler(priority = EventPriority.HIGH)
        fun onEvent(event: SimpleEvent) {
            order.add("high")
        }
    }

    private class BrokenListener(
        private val order: MutableList<String>,
    ) {
        @EventHandler(priority = EventPriority.NORMAL)
        fun onEvent(event: SimpleEvent) {
            order.add("broken")
            error("listener failure")
        }
    }

    private class HighCancelledListener(
        private val order: MutableList<String>,
    ) {
        @EventHandler(priority = EventPriority.HIGH)
        fun onEvent(event: CancellableEvent) {
            order.add("ignored")
        }
    }

    private class CancelListener(
        private val order: MutableList<String>,
    ) {
        @EventHandler(priority = EventPriority.NORMAL)
        fun onEvent(event: CancellableEvent) {
            event.isCancelled = true
            order.add("cancel")
        }
    }

    private class IgnoreCancelledListener(
        private val order: MutableList<String>,
    ) {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onEvent(event: CancellableEvent) {
            order.add("monitor")
        }
    }
}
