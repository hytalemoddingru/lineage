/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe command registry.
 */
class CommandRegistryImpl : CommandRegistry {
    private val lock = Any()
    private val entries = LinkedHashMap<String, CommandEntry>()
    private val names = HashMap<String, CommandEntry>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override fun register(command: Command) {
        register(command, DEFAULT_OWNER_ID)
    }

    fun register(command: Command, ownerId: String) {
        val entry = buildEntry(command, ownerId)
        synchronized(lock) {
            for (name in entry.allNamesLowercase) {
                if (names.containsKey(name)) {
                    throw IllegalArgumentException("Command name already registered: $name")
                }
            }
            entries[entry.id] = entry
            for (name in entry.allNamesLowercase) {
                names[name] = entry
            }
        }
        notifyListeners()
    }

    override fun unregister(name: String) {
        val normalized = normalizeName(name) ?: return
        synchronized(lock) {
            val existing = names[normalized] ?: return
            for (alias in existing.allNamesLowercase) {
                names.remove(alias)
            }
            entries.remove(existing.id)
        }
        notifyListeners()
    }

    override fun get(name: String): Command? {
        val normalized = normalizeName(name) ?: return null
        return synchronized(lock) { names[normalized]?.command }
    }

    fun snapshot(): List<CommandEntry> {
        return synchronized(lock) { entries.values.toList() }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            listener()
        }
    }

    private fun buildEntry(command: Command, ownerId: String): CommandEntry {
        val namespace = normalizeOwner(ownerId)
        require(command.name.isNotBlank()) { "Command name must not be blank" }
        require(command.usage.isNotBlank()) { "Command usage must not be blank: ${command.name}" }
        val baseNames = (listOf(command.name) + command.aliases).map { normalizeBaseName(it) }
        val unique = baseNames.toSet()
        require(unique.size == baseNames.size) { "Command names must be unique: ${command.name}" }
        val namespaced = baseNames.map { "$namespace:$it" }
        return CommandEntry(
            id = "$namespace:${baseNames.first()}",
            ownerId = namespace,
            command = command,
            baseNames = baseNames,
            namespacedNames = namespaced,
        )
    }

    private fun normalizeName(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return trimmed.lowercase()
    }

    private fun normalizeOwner(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Command owner must not be blank" }
        require(OWNER_PATTERN.matches(trimmed)) { "Command owner has invalid characters: $value" }
        return trimmed.lowercase()
    }

    private fun normalizeBaseName(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Command name must not be blank" }
        require(trimmed == value) { "Command name must not include surrounding whitespace: $value" }
        require(!trimmed.contains(' ')) { "Command name must not contain spaces: $value" }
        require(!trimmed.contains('/')) { "Command name must not contain '/': $value" }
        require(!trimmed.contains(':')) { "Command name must not contain ':': $value" }
        require(NAME_PATTERN.matches(trimmed)) { "Command name has invalid characters: $value" }
        return trimmed.lowercase()
    }

    companion object {
        private const val DEFAULT_OWNER_ID = "lineage"
        private val NAME_PATTERN = Regex("^[\\p{L}\\p{N}_.\\-?]+$")
        private val OWNER_PATTERN = Regex("^[A-Za-z0-9_.-]+$")
    }
}

data class CommandEntry(
    val id: String,
    val ownerId: String,
    val command: Command,
    val baseNames: List<String>,
    val namespacedNames: List<String>,
) {
    val allNamesLowercase: List<String> = (baseNames + namespacedNames).map { it.lowercase() }
}
