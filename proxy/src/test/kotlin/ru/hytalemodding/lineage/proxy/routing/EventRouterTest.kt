/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.routing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.event.EventHandler
import ru.hytalemodding.lineage.api.event.routing.RoutePostSelectEvent
import ru.hytalemodding.lineage.api.event.routing.RoutePreSelectEvent
import ru.hytalemodding.lineage.api.protocol.ClientType
import ru.hytalemodding.lineage.api.routing.RouteSelectionReason
import ru.hytalemodding.lineage.api.routing.RoutingContext
import ru.hytalemodding.lineage.proxy.config.BackendConfig
import ru.hytalemodding.lineage.proxy.event.EventBusImpl
import ru.hytalemodding.lineage.proxy.security.InFlightLimiter
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EventRouterTest {
    @Test
    fun preEventOverridesSelectionAndPostsAfter() {
        val backendA = BackendConfig(id = "a", host = "127.0.0.1", port = 25566)
        val backendB = BackendConfig(id = "b", host = "127.0.0.1", port = 25567)
        val delegate = StubRouter(mapOf("a" to backendA, "b" to backendB), "a")
        val eventBus = EventBusImpl()
        val events = mutableListOf<String>()

        val listener = object {
            @EventHandler
            fun onPre(event: RoutePreSelectEvent) {
                events.add("pre")
                event.decision.suggestBackend("b")
            }

            @EventHandler
            fun onPost(event: RoutePostSelectEvent) {
                events.add("post")
                assertEquals("b", event.selectedBackendId)
                assertEquals(RouteSelectionReason.OVERRIDE, event.reason)
            }
        }
        eventBus.register(listener)

        val router = EventRouter(delegate, eventBus)
        val selected = router.selectBackend(context())

        assertEquals("b", selected.id)
        assertEquals(listOf("pre", "post"), events)
    }

    @Test
    fun preEventCanDenyRouting() {
        val backendA = BackendConfig(id = "a", host = "127.0.0.1", port = 25566)
        val delegate = StubRouter(mapOf("a" to backendA), "a")
        val eventBus = EventBusImpl()

        val listener = object {
            @EventHandler
            fun onPre(event: RoutePreSelectEvent) {
                event.decision.deny("blocked")
            }
        }
        eventBus.register(listener)

        val router = EventRouter(delegate, eventBus)
        assertThrows(RoutingDeniedException::class.java) {
            router.selectBackend(context())
        }
    }

    @Test
    fun finalizedRoutingDecisionCannotBeOverriddenByLaterListener() {
        val backendA = BackendConfig(id = "a", host = "127.0.0.1", port = 25566)
        val backendB = BackendConfig(id = "b", host = "127.0.0.1", port = 25567)
        val delegate = StubRouter(mapOf("a" to backendA, "b" to backendB), "a")
        val eventBus = EventBusImpl()

        val first = object {
            @EventHandler
            fun onPre(event: RoutePreSelectEvent) {
                event.decision.suggestBackend("b")
            }
        }
        val second = object {
            @EventHandler
            fun onPre(event: RoutePreSelectEvent) {
                event.decision.deny("must-not-override")
            }
        }
        eventBus.register(first)
        eventBus.register(second)

        val router = EventRouter(delegate, eventBus)
        val selected = router.selectBackend(context())

        assertEquals("b", selected.id)
    }

    @Test
    fun rejectsWhenRoutingPipelineIsOverloaded() {
        val backendA = BackendConfig(id = "a", host = "127.0.0.1", port = 25566)
        val delegate = StubRouter(mapOf("a" to backendA), "a")
        val eventBus = EventBusImpl()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blockingListener = object {
            @EventHandler
            fun onPre(event: RoutePreSelectEvent) {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
            }
        }
        eventBus.register(blockingListener)

        val router = EventRouter(delegate, eventBus, inFlightLimiter = InFlightLimiter(1))
        var failure: RoutingDeniedException? = null
        val first = Thread {
            router.selectBackend(context())
        }
        first.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        try {
            router.selectBackend(context())
        } catch (ex: RoutingDeniedException) {
            failure = ex
        } finally {
            release.countDown()
            first.join(2_000)
        }

        assertTrue(failure != null)
        assertEquals("Routing pipeline is overloaded", failure?.reason)
    }

    private fun context(): RoutingContext {
        return RoutingContext(
            playerId = UUID.randomUUID(),
            username = "test",
            clientAddress = InetSocketAddress("127.0.0.1", 12345),
            requestedBackendId = null,
            protocolCrc = 0,
            protocolBuild = 0,
            clientVersion = "test",
            clientType = ClientType.UNKNOWN,
            language = "en-US",
            identityTokenPresent = false,
        )
    }

    private class StubRouter(
        private val backends: Map<String, BackendConfig>,
        private val defaultId: String,
    ) : Router {
        override fun selectInitialBackend(context: RoutingContext): BackendConfig {
            return backends[defaultId]!!
        }

        override fun selectBackend(context: RoutingContext): BackendConfig {
            return backends[context.requestedBackendId ?: defaultId]!!
        }

        override fun findBackend(id: String): BackendConfig? = backends[id]
    }
}
