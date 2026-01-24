/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.command

import com.hypixel.hytale.server.core.command.system.CommandManager
import com.hypixel.hytale.server.core.command.system.CommandRegistration
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import org.slf4j.LoggerFactory
import ru.hytalemodding.lineage.backend.messaging.BackendMessaging
import ru.hytalemodding.lineage.shared.command.ProxyCommandDescriptor
import ru.hytalemodding.lineage.shared.command.ProxyCommandFlags
import ru.hytalemodding.lineage.shared.command.ProxyCommandRegistryProtocol

class ProxyCommandBridge(
    private val plugin: JavaPlugin,
    private val isMessagingEnabled: () -> Boolean,
) {
    private val logger = LoggerFactory.getLogger(ProxyCommandBridge::class.java)
    private val registrations = ArrayList<CommandRegistration>()

    fun start() {
        BackendMessaging.registerChannel(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID, this::handleSnapshot)
    }

    fun stop() {
        BackendMessaging.unregisterChannel(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID)
        clearRegistrations()
    }

    fun requestSync() {
        BackendMessaging.send(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, ProxyCommandRegistryProtocol.encodeRequest())
    }

    private fun handleSnapshot(payload: ByteArray) {
        val snapshot = ProxyCommandRegistryProtocol.decodeSnapshot(payload) ?: return
        applySnapshot(snapshot.commands)
    }

    private fun applySnapshot(commands: List<ProxyCommandDescriptor>) {
        clearRegistrations()
        for (descriptor in commands) {
            registerDescriptor(descriptor)
        }
    }

    private fun registerDescriptor(descriptor: ProxyCommandDescriptor) {
        val namespace = descriptor.namespace.trim()
        val name = descriptor.name.trim()
        if (namespace.isEmpty() || name.isEmpty()) {
            return
        }
        val usage = descriptor.usage.ifBlank { name }
        val description = descriptor.description.ifBlank { usage }
        val namespacedName = "$namespace:$name"
        val namespacedAliases = descriptor.aliases.mapNotNull { alias ->
            val trimmed = alias.trim()
            if (trimmed.isEmpty()) null else "$namespace:$trimmed"
        }.distinct().filter { it != namespacedName }
        registerCommand(namespacedName, namespacedAliases, description, usage, descriptor.flags)
        if (descriptor.flags and ProxyCommandFlags.HIDDEN != 0) {
            return
        }
        if (!isNameAvailable(name)) {
            logger.warn("Command /{} is already registered; using /{}:{} only.", name, namespace, name)
            return
        }
        val baseAliases = descriptor.aliases.mapNotNull { alias ->
            val trimmed = alias.trim()
            if (trimmed.isEmpty()) {
                return@mapNotNull null
            }
            if (!isNameAvailable(trimmed)) {
                logger.warn("Command alias /{} is already registered; skipping alias for /{}.", trimmed, name)
                return@mapNotNull null
            }
            trimmed
        }.distinct().filter { it != name }
        registerCommand(name, baseAliases, description, usage, descriptor.flags)
    }

    private fun registerCommand(
        name: String,
        aliases: List<String>,
        description: String,
        usage: String,
        flags: Int,
    ) {
        if (!isNameAvailable(name)) {
            logger.warn("Command /{} is already registered; skipping.", name)
            return
        }
        val command = ProxyCommandBridgeCommand(
            name = name,
            description = description,
            usage = usage,
            aliases = aliases,
            flags = flags,
            isMessagingEnabled = isMessagingEnabled,
        )
        val registration = plugin.commandRegistry.registerCommand(command)
        if (registration != null) {
            registrations.add(registration)
        }
    }

    private fun isNameAvailable(name: String): Boolean {
        return !CommandManager.get().getCommandRegistration().containsKey(name.lowercase())
    }

    private fun clearRegistrations() {
        for (registration in registrations) {
            registration.unregister()
        }
        registrations.clear()
    }
}
