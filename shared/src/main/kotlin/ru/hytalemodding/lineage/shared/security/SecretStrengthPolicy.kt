/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.security

/**
 * Central policy for rejecting known default or weak shared-secret values.
 */
object SecretStrengthPolicy {
    private const val DEFAULT_PROXY_SECRET_NORMALIZED = "changemeplease"
    private val weakSecrets = setOf(
        DEFAULT_PROXY_SECRET_NORMALIZED,
        "changeme",
        "password",
        "secret",
    )

    fun validationError(secret: String, fieldPath: String): String? {
        val normalized = normalize(secret)
        if (normalized in weakSecrets || normalized.startsWith("changeme")) {
            return "$fieldPath must not use a default or weak secret value"
        }
        return null
    }

    private fun normalize(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                if (ch.isLetterOrDigit()) {
                    append(ch.lowercaseChar())
                }
            }
        }
    }
}
