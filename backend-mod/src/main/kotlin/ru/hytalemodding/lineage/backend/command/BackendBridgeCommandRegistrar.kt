/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.command

import com.hypixel.hytale.server.core.command.system.CommandManager
import com.hypixel.hytale.server.core.plugin.JavaPlugin

internal fun interface BackendBridgeRegistration {
    fun unregister()
}

internal interface BackendBridgeCommandRegistrar {
    fun isNameAvailable(name: String): Boolean
    fun register(definition: BackendBridgeCommandDefinition): BackendBridgeRegistration?
}

internal data class BackendBridgeCommandDefinition(
    val name: String,
    val aliases: List<String>,
    val description: String,
    val usage: String,
    val flags: Int,
    val isMessagingEnabled: () -> Boolean,
    val isRegistrySynchronized: () -> Boolean,
)

internal class HytaleBackendBridgeCommandRegistrar(
    private val plugin: JavaPlugin,
) : BackendBridgeCommandRegistrar {
    override fun isNameAvailable(name: String): Boolean {
        return !CommandManager.get().commandRegistration.containsKey(name.lowercase())
    }

    override fun register(definition: BackendBridgeCommandDefinition): BackendBridgeRegistration? {
        val command = ProxyCommandBridgeCommand(
            name = definition.name,
            description = definition.description,
            usage = definition.usage,
            aliases = definition.aliases,
            flags = definition.flags,
            isMessagingEnabled = definition.isMessagingEnabled,
            isRegistrySynchronized = definition.isRegistrySynchronized,
        )
        val registration = plugin.commandRegistry.registerCommand(command) ?: return null
        return BackendBridgeRegistration { registration.unregister() }
    }
}
