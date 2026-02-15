/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.command

internal data class ProxyCommandDispatchDecision(
    val accepted: Boolean,
    val normalizedInput: String?,
    val message: String,
)

internal object ProxyCommandDispatchPolicy {
    fun evaluate(
        isPlayerSender: Boolean,
        messagingEnabled: Boolean,
        registrySynchronized: Boolean,
        rawInput: String,
        usage: String,
    ): ProxyCommandDispatchDecision {
        if (!isPlayerSender) {
            return ProxyCommandDispatchDecision(
                accepted = false,
                normalizedInput = null,
                message = "Command is only available to players.",
            )
        }
        if (!messagingEnabled) {
            return ProxyCommandDispatchDecision(
                accepted = false,
                normalizedInput = null,
                message = "Proxy messaging is disabled.",
            )
        }
        if (!registrySynchronized) {
            return ProxyCommandDispatchDecision(
                accepted = false,
                normalizedInput = null,
                message = "Proxy command registry is not synchronized.",
            )
        }
        val normalized = rawInput.trim()
        if (normalized.isBlank()) {
            return ProxyCommandDispatchDecision(
                accepted = false,
                normalizedInput = null,
                message = "Usage: $usage",
            )
        }
        return ProxyCommandDispatchDecision(
            accepted = true,
            normalizedInput = normalized,
            message = "",
        )
    }
}
