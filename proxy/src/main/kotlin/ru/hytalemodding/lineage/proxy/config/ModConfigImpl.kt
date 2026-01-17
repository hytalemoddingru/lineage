/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.config

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import ru.hytalemodding.lineage.api.config.ModConfig
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * TOML-backed mod configuration.
 */
class ModConfigImpl(
    override val name: String,
    override val path: Path,
    private val defaults: (() -> String)? = null,
    createIfMissing: Boolean = false,
) : ModConfig {
    private var data: MutableMap<String, Any?> = mutableMapOf()

    init {
        if (Files.exists(path)) {
            reload()
        } else if (createIfMissing) {
            writeDefaultsIfAny()
            if (Files.exists(path)) {
                reload()
            }
        }
    }

    override fun reload(): ModConfig {
        if (!Files.exists(path)) {
            data = mutableMapOf()
            return this
        }
        Files.newBufferedReader(path).use { reader ->
            data = parse(reader).toMutableMap()
        }
        return this
    }

    override fun save() {
        path.parent?.let { Files.createDirectories(it) }
        if (!Files.exists(path) && defaults != null && data.isEmpty()) {
            writeDefaultsIfAny()
            return
        }
        val content = TomlWriter.write(data)
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    override fun contains(path: String): Boolean = resolveNode(path) != null

    override fun remove(path: String) {
        if (path.isBlank()) {
            return
        }
        removeNode(path)
    }

    override fun set(path: String, value: Any?) {
        if (value == null) {
            remove(path)
            return
        }
        if (path.isBlank()) {
            throw IllegalArgumentException("Config path must not be blank")
        }
        setNode(path, normalizeValue(value))
    }

    override fun getString(path: String): String? = resolveNode(path) as? String

    override fun getString(path: String, defaultValue: String): String =
        getString(path) ?: defaultValue

    override fun getInt(path: String): Int? = (resolveNode(path) as? Long)?.toInt()

    override fun getInt(path: String, defaultValue: Int): Int =
        getInt(path) ?: defaultValue

    override fun getLong(path: String): Long? = resolveNode(path) as? Long

    override fun getLong(path: String, defaultValue: Long): Long =
        getLong(path) ?: defaultValue

    override fun getBoolean(path: String): Boolean? = resolveNode(path) as? Boolean

    override fun getBoolean(path: String, defaultValue: Boolean): Boolean =
        getBoolean(path) ?: defaultValue

    override fun getDuration(path: String): Duration? {
        val value = resolveNode(path) as? String ?: return null
        return Duration.parse(value)
    }

    override fun getDuration(path: String, defaultValue: Duration): Duration =
        getDuration(path) ?: defaultValue

    override fun getStringList(path: String): List<String> =
        (resolveNode(path) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    private fun parse(reader: Reader): TomlTable {
        val result = Toml.parse(reader)
        if (result.hasErrors()) {
            val errors = result.errors().joinToString("; ") { it.toString() }
            throw IllegalArgumentException("Failed to parse config $path: $errors")
        }
        return result
    }

    private fun writeDefaultsIfAny() {
        path.parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
            writer.write(defaults?.invoke() ?: "")
        }
    }

    private fun resolveNode(path: String): Any? {
        if (path.isBlank()) {
            return null
        }
        val parts = path.split('.')
        var node: Any? = data
        for (part in parts) {
            if (node !is Map<*, *>) {
                return null
            }
            node = node[part]
        }
        return node
    }

    private fun setNode(path: String, value: Any?) {
        val parts = path.split('.')
        var current: MutableMap<String, Any?> = data
        for (part in parts.dropLast(1)) {
            val next = current[part]
            if (next is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                current = next as MutableMap<String, Any?>
            } else {
                val created = mutableMapOf<String, Any?>()
                current[part] = created
                current = created
            }
        }
        current[parts.last()] = value
    }

    private fun removeNode(path: String) {
        val parts = path.split('.')
        val stack = ArrayDeque<MutableMap<String, Any?>>()
        var current: MutableMap<String, Any?> = data
        stack.add(current)
        for (part in parts.dropLast(1)) {
            val next = current[part]
            if (next !is MutableMap<*, *>) {
                return
            }
            @Suppress("UNCHECKED_CAST")
            current = next as MutableMap<String, Any?>
            stack.add(current)
        }
        current.remove(parts.last())
        cleanupEmptyMaps(parts, stack)
    }

    private fun cleanupEmptyMaps(parts: List<String>, stack: ArrayDeque<MutableMap<String, Any?>>) {
        for (index in parts.indices.reversed()) {
            val map = stack.removeLast()
            if (map.isNotEmpty() || stack.isEmpty()) {
                return
            }
            val parent = stack.last()
            parent.remove(parts[index - 1])
        }
    }

    private fun normalizeValue(value: Any): Any? {
        return when (value) {
            is String -> value
            is Int -> value.toLong()
            is Long -> value
            is Double -> value
            is Boolean -> value
            is Duration -> value.toString()
            is Enum<*> -> value.name
            is List<*> -> value.mapNotNull { it?.let { normalizeValue(it) } }
            is Map<*, *> -> value.entries.associate { entry ->
                val key = entry.key as? String
                    ?: throw IllegalArgumentException("Config map keys must be strings")
                key to entry.value?.let { normalizeValue(it) }
            }.toMutableMap()
            else -> throw IllegalArgumentException("Unsupported config value type: ${value.javaClass.name}")
        }
    }

    private fun TomlTable.toMutableMap(): MutableMap<String, Any?> {
        val output = mutableMapOf<String, Any?>()
        for (key in keySet()) {
            val value = get(key)
            output[key] = when (value) {
                is TomlTable -> value.toMutableMap()
                is TomlArray -> value.toList().map { element ->
                    when (element) {
                        is TomlTable -> element.toMutableMap()
                        is TomlArray -> element.toList()
                        else -> element
                    }
                }
                else -> value
            }
        }
        return output
    }
}
