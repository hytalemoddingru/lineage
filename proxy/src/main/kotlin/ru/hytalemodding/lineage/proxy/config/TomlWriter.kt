/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.config

import java.time.Duration

/**
 * Serializes simple maps into TOML.
 */
object TomlWriter {
    fun write(input: Map<String, Any?>): String {
        val builder = StringBuilder()
        renderTable(builder, emptyList(), input)
        return builder.toString().trimEnd() + "\n"
    }

    private fun renderTable(builder: StringBuilder, path: List<String>, table: Map<String, Any?>) {
        val simple = table.filterValues { it !is Map<*, *> }
        for ((key, value) in simple.toSortedMap()) {
            builder.append(key)
            builder.append(" = ")
            builder.append(formatValue(value))
            builder.append("\n")
        }

        val nested = table.filterValues { it is Map<*, *> }
        for ((key, value) in nested.toSortedMap()) {
            @Suppress("UNCHECKED_CAST")
            val map = value as Map<String, Any?>
            if (builder.isNotEmpty()) {
                builder.append("\n")
            }
            val fullPath = (path + key).joinToString(".")
            builder.append("[")
            builder.append(fullPath)
            builder.append("]\n")
            renderTable(builder, path + key, map)
        }
    }

    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "\"\""
            is String -> quote(value)
            is Boolean -> value.toString()
            is Int -> value.toLong().toString()
            is Long -> value.toString()
            is Double -> value.toString()
            is Duration -> quote(value.toString())
            is Enum<*> -> quote(value.name)
            is List<*> -> formatList(value)
            else -> quote(value.toString())
        }
    }

    private fun formatList(values: List<*>): String {
        if (values.isEmpty()) {
            return "[]"
        }
        val rendered = values.map { value ->
            when (value) {
                is String -> quote(value)
                is Boolean -> value.toString()
                is Int -> value.toLong().toString()
                is Long -> value.toString()
                is Double -> value.toString()
                is Duration -> quote(value.toString())
                is Enum<*> -> quote(value.name)
                else -> throw IllegalArgumentException("Unsupported list item type: ${value?.javaClass?.name}")
            }
        }
        return rendered.joinToString(prefix = "[", postfix = "]", separator = ", ")
    }

    private fun quote(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
