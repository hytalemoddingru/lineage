/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProxyCommandDispatchPolicyTest {
    @Test
    fun rejectsNonPlayerSenderDeterministically() {
        val decision = ProxyCommandDispatchPolicy.evaluate(
            isPlayerSender = false,
            messagingEnabled = true,
            registrySynchronized = true,
            rawInput = "mod reload all",
            usage = "mod reload <id|all>",
        )

        assertFalse(decision.accepted)
        assertEquals(null, decision.normalizedInput)
        assertEquals("Command is only available to players.", decision.message)
    }

    @Test
    fun rejectsWhenMessagingDisabledDeterministically() {
        val decision = ProxyCommandDispatchPolicy.evaluate(
            isPlayerSender = true,
            messagingEnabled = false,
            registrySynchronized = true,
            rawInput = "mod reload all",
            usage = "mod reload <id|all>",
        )

        assertFalse(decision.accepted)
        assertEquals(null, decision.normalizedInput)
        assertEquals("Proxy messaging is disabled.", decision.message)
    }

    @Test
    fun rejectsWhenRegistryIsNotSynchronizedDeterministically() {
        val decision = ProxyCommandDispatchPolicy.evaluate(
            isPlayerSender = true,
            messagingEnabled = true,
            registrySynchronized = false,
            rawInput = "mod reload all",
            usage = "mod reload <id|all>",
        )

        assertFalse(decision.accepted)
        assertEquals(null, decision.normalizedInput)
        assertEquals("Proxy command registry is not synchronized.", decision.message)
    }

    @Test
    fun rejectsBlankInputDeterministically() {
        val decision = ProxyCommandDispatchPolicy.evaluate(
            isPlayerSender = true,
            messagingEnabled = true,
            registrySynchronized = true,
            rawInput = "   ",
            usage = "mod reload <id|all>",
        )

        assertFalse(decision.accepted)
        assertEquals(null, decision.normalizedInput)
        assertEquals("Usage: mod reload <id|all>", decision.message)
    }

    @Test
    fun acceptsValidPlayerRequest() {
        val decision = ProxyCommandDispatchPolicy.evaluate(
            isPlayerSender = true,
            messagingEnabled = true,
            registrySynchronized = true,
            rawInput = "  mod reload all  ",
            usage = "mod reload <id|all>",
        )

        assertTrue(decision.accepted)
        assertEquals("mod reload all", decision.normalizedInput)
        assertEquals("", decision.message)
    }
}
