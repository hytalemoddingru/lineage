/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.logging

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StructuredLogTest {
    @Test
    fun rendersStructuredEventWithCorrelationId() {
        val event = StructuredLog.event(
            category = "control-plane",
            severity = "WARN",
            reason = "INVALID_TIMESTAMP",
            correlationId = "corr-1",
            fields = mapOf("senderId" to "backend-1", "messageType" to "TOKEN_VALIDATION"),
        )

        assertTrue(event.contains("category=control-plane"))
        assertTrue(event.contains("severity=WARN"))
        assertTrue(event.contains("reason=INVALID_TIMESTAMP"))
        assertTrue(event.contains("correlationId=corr-1"))
        assertTrue(event.contains("senderId=backend-1"))
        assertTrue(event.contains("messageType=TOKEN_VALIDATION"))
    }

    @Test
    fun redactsSensitiveValues() {
        val event = StructuredLog.event(
            category = "security",
            severity = "WARN",
            reason = "MALFORMED_TOKEN",
            fields = mapOf(
                "rawToken" to "abc123",
                "proxy_secret" to "very-secret",
                "playerId" to "player-1",
            ),
        )

        assertTrue(event.contains("rawToken=<redacted>"))
        assertTrue(event.contains("proxy_secret=<redacted>"))
        assertTrue(event.contains("playerId=player-1"))
        assertTrue(!event.contains("abc123"))
        assertTrue(!event.contains("very-secret"))
    }
}

