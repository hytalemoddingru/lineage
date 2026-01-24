/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.event.routing

import ru.hytalemodding.lineage.api.event.Event
import ru.hytalemodding.lineage.api.routing.RouteSelectionReason
import ru.hytalemodding.lineage.api.routing.RoutingContext
import ru.hytalemodding.lineage.api.routing.RoutingDecision

data class RoutePreSelectEvent(
    val context: RoutingContext,
    val decision: RoutingDecision,
) : Event

data class RoutePostSelectEvent(
    val context: RoutingContext,
    val selectedBackendId: String,
    val reason: RouteSelectionReason,
) : Event
