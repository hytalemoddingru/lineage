/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.routing

/**
 * One-shot routing decision for pre-selection hooks.
 */
class RoutingDecision {
    var overrideBackendId: String? = null
        private set
    var denyReason: String? = null
        private set
    private var decided = false

    fun suggestBackend(id: String) {
        ensureNotDecided()
        overrideBackendId = id
        decided = true
    }

    fun deny(reason: String) {
        ensureNotDecided()
        denyReason = reason
        decided = true
    }

    private fun ensureNotDecided() {
        check(!decided) { "Routing decision already finalized" }
    }
}
