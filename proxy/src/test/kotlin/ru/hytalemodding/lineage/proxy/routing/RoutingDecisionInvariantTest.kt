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
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.routing.RoutingDecision

class RoutingDecisionInvariantTest {
    @Test
    fun decisionCannotBeChangedAfterBackendSuggestion() {
        val decision = RoutingDecision()
        decision.suggestBackend("hub")

        val ex = assertThrows(IllegalStateException::class.java) {
            decision.deny("blocked")
        }
        assertEquals("Routing decision already finalized", ex.message)
        assertEquals("hub", decision.overrideBackendId)
        assertEquals(null, decision.denyReason)
    }

    @Test
    fun decisionCannotBeChangedAfterDeny() {
        val decision = RoutingDecision()
        decision.deny("blocked")

        val ex = assertThrows(IllegalStateException::class.java) {
            decision.suggestBackend("hub")
        }
        assertEquals("Routing decision already finalized", ex.message)
        assertEquals(null, decision.overrideBackendId)
        assertEquals("blocked", decision.denyReason)
    }
}
