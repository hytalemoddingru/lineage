/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.event

/**
 * Registers listeners and dispatches events.
 */
interface EventBus {
    fun register(listener: Any)
    fun unregister(listener: Any)
    fun post(event: Event)
}
