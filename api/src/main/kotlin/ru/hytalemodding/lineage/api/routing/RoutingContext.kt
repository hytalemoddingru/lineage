/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.routing

import java.net.InetSocketAddress
import java.util.UUID
import ru.hytalemodding.lineage.api.protocol.ClientType

/**
 * Routing context passed to routing strategies.
 */
data class RoutingContext(
    val playerId: UUID?,
    val username: String?,
    val clientAddress: InetSocketAddress?,
    val requestedBackendId: String?,
    val protocolCrc: Int,
    val protocolBuild: Int,
    val clientVersion: String,
    val clientType: ClientType,
    val language: String,
    val identityTokenPresent: Boolean,
)
