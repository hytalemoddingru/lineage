/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.logging

object StructuredLog {
    fun event(
        category: String,
        severity: String,
        reason: String,
        correlationId: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): String {
        val tokens = ArrayList<String>(fields.size + 4)
        tokens.add("category=${sanitize(category)}")
        tokens.add("severity=${sanitize(severity)}")
        tokens.add("reason=${sanitize(reason)}")
        if (!correlationId.isNullOrBlank()) {
            tokens.add("correlationId=${sanitize(correlationId)}")
        }
        fields.entries.sortedBy { it.key }.forEach { (key, value) ->
            val renderedValue = if (isSensitiveKey(key)) {
                REDACTED_VALUE
            } else {
                sanitize(value?.toString() ?: "null")
            }
            tokens.add("${sanitize(key)}=$renderedValue")
        }
        return tokens.joinToString(" ")
    }

    private fun sanitize(value: String): String {
        return value
            .trim()
            .replace(Regex("\\s+"), "_")
            .replace("\"", "'")
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase()
        return normalized.contains("token") || normalized.contains("secret")
    }

    private const val REDACTED_VALUE = "<redacted>"
}

