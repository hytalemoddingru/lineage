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
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe command registry.
 */
class CommandRegistryImpl : CommandRegistry {
    private val commands = ConcurrentHashMap<String, Command>()

    override fun register(command: Command) {
        val names = listOf(command.name) + command.aliases
        for (name in names) {
            val key = name.lowercase()
            if (commands.containsKey(key)) {
                throw IllegalArgumentException("Command name already registered: $name")
            }
            commands[key] = command
        }
    }

    override fun unregister(name: String) {
        val command = commands.remove(name.lowercase()) ?: return
        val names = listOf(command.name) + command.aliases
        for (alias in names) {
            commands.remove(alias.lowercase())
        }
    }

    override fun get(name: String): Command? = commands[name.lowercase()]
}
