/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.routing

import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.event.routing.RoutePostSelectEvent
import ru.hytalemodding.lineage.api.event.routing.RoutePreSelectEvent
import ru.hytalemodding.lineage.api.routing.RouteSelectionReason
import ru.hytalemodding.lineage.api.routing.RoutingContext
import ru.hytalemodding.lineage.api.routing.RoutingDecision
import ru.hytalemodding.lineage.proxy.config.BackendConfig
import ru.hytalemodding.lineage.proxy.observability.ProxyMetricsRegistry
import ru.hytalemodding.lineage.proxy.security.InFlightLimiter

class EventRouter(
    private val delegate: Router,
    private val eventBus: EventBus,
    private val metrics: ProxyMetricsRegistry? = null,
    private val inFlightLimiter: InFlightLimiter? = null,
) : Router {
    override fun selectInitialBackend(context: RoutingContext): BackendConfig {
        return select(context, true)
    }

    override fun selectBackend(context: RoutingContext): BackendConfig {
        return select(context, false)
    }

    override fun findBackend(id: String): BackendConfig? = delegate.findBackend(id)

    private fun select(context: RoutingContext, initial: Boolean): BackendConfig {
        val lease = inFlightLimiter?.tryAcquire() ?: if (inFlightLimiter == null) null else {
            throw RoutingDeniedException("Routing pipeline is overloaded")
        }
        val decision = RoutingDecision()
        try {
            eventBus.post(RoutePreSelectEvent(context, decision))
            val denyReason = decision.denyReason
            if (denyReason != null) {
                throw RoutingDeniedException(denyReason)
            }
            val overrideId = decision.overrideBackendId
            val selected = if (overrideId != null) {
                delegate.findBackend(overrideId)
                    ?: throw IllegalStateException("Unknown backend override: $overrideId")
            } else {
                if (initial) delegate.selectInitialBackend(context) else delegate.selectBackend(context)
            }
            val reason = when {
                overrideId != null -> RouteSelectionReason.OVERRIDE
                context.requestedBackendId != null -> RouteSelectionReason.TRANSFER
                else -> RouteSelectionReason.STRATEGY
            }
            metrics?.incrementRoutingDecision(reason.name)
            eventBus.post(RoutePostSelectEvent(context, selected.id, reason))
            return selected
        } finally {
            lease?.close()
        }
    }
}

class RoutingDeniedException(
    val reason: String,
) : RuntimeException(reason)
