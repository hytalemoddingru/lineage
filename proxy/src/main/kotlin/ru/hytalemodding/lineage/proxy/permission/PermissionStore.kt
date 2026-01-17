/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.permission

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import ru.hytalemodding.lineage.proxy.config.ConfigException
import ru.hytalemodding.lineage.proxy.config.TomlWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Persists proxy permissions in a TOML file.
 */
class PermissionStore(
    private val path: Path,
) {
    fun load(): Map<String, Set<String>> {
        if (!Files.exists(path)) {
            return emptyMap()
        }
        Files.newBufferedReader(path).use { reader ->
            val result = Toml.parse(reader)
            if (result.hasErrors()) {
                val errors = result.errors().joinToString("; ") { it.toString() }
                throw ConfigException("Failed to parse permissions: $errors")
            }
            return readPermissions(result)
        }
    }

    fun save(permissions: Map<String, Set<String>>) {
        path.parent?.let { Files.createDirectories(it) }
        val normalized = permissions.mapValues { (_, values) ->
            values.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
        }
        val content = TomlWriter.write(mapOf("permissions" to normalized))
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    private fun readPermissions(result: TomlParseResult): Map<String, Set<String>> {
        val table = result.getTable("permissions") ?: return emptyMap()
        return table.toPermissionMap()
    }

    private fun TomlTable.toPermissionMap(): Map<String, Set<String>> {
        val output = mutableMapOf<String, Set<String>>()
        for (key in keySet()) {
            val raw = get(key)
            val values = when (raw) {
                is TomlArray -> raw.toList().mapNotNull { it as? String }
                is String -> listOf(raw)
                else -> emptyList()
            }
            val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isNotEmpty()) {
                output[key] = cleaned.toSet()
            }
        }
        return output
    }
}
