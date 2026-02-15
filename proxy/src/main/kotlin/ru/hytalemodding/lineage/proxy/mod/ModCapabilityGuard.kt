/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import ru.hytalemodding.lineage.api.backend.BackendInfo
import ru.hytalemodding.lineage.api.backend.BackendRegistry
import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandRegistry
import ru.hytalemodding.lineage.proxy.command.CommandRegistryImpl
import ru.hytalemodding.lineage.api.event.Event
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.messaging.Channel
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.mod.ModCapability
import ru.hytalemodding.lineage.api.mod.ModInfo
import ru.hytalemodding.lineage.api.permission.PermissionChecker
import ru.hytalemodding.lineage.api.permission.PermissionSubject
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.api.player.ProxyPlayer
import ru.hytalemodding.lineage.api.schedule.Scheduler
import ru.hytalemodding.lineage.api.schedule.TaskHandle
import ru.hytalemodding.lineage.api.service.ServiceKey
import ru.hytalemodding.lineage.api.service.ServiceRegistry
import ru.hytalemodding.lineage.proxy.event.HandlerScanner
import java.time.Duration
import java.util.UUID

class ModCapabilityGuard(
    private val modInfo: ModInfo,
) {
    val modId: String = modInfo.id

    fun require(capability: ModCapability) {
        if (!modInfo.capabilities.contains(capability)) {
            throw IllegalStateException("Mod ${modInfo.id} lacks capability $capability")
        }
    }
}

class GuardedEventBus(
    private val delegate: EventBus,
    private val guard: ModCapabilityGuard,
) : EventBus {
    override fun register(listener: Any) {
        val methods = HandlerScanner.scan(listener)
        for (method in methods) {
            val required = requiredCapability(method.eventType)
            if (required != null) {
                guard.require(required)
            }
        }
        delegate.register(listener)
    }

    override fun unregister(listener: Any) {
        delegate.unregister(listener)
    }

    override fun post(event: Event) {
        throw IllegalStateException("Mod ${guard.modId} cannot post events")
    }

    private fun requiredCapability(eventType: Class<*>): ModCapability? {
        val name = eventType.name
        return when {
            name.startsWith(ROUTING_EVENT_PREFIX) -> ModCapability.ROUTING
            name.startsWith(SECURITY_EVENT_PREFIX) -> ModCapability.SECURITY
            else -> null
        }
    }

    private companion object {
        const val ROUTING_EVENT_PREFIX = "ru.hytalemodding.lineage.api.event.routing."
        const val SECURITY_EVENT_PREFIX = "ru.hytalemodding.lineage.api.event.security."
    }
}

class GuardedCommandRegistry(
    private val delegate: CommandRegistry,
    private val guard: ModCapabilityGuard,
) : CommandRegistry {
    override fun register(command: Command) {
        guard.require(ModCapability.COMMANDS)
        if (delegate is CommandRegistryImpl) {
            delegate.register(command, guard.modId)
        } else {
            delegate.register(command)
        }
    }

    override fun unregister(name: String) {
        guard.require(ModCapability.COMMANDS)
        delegate.unregister(name)
    }

    override fun get(name: String): Command? {
        guard.require(ModCapability.COMMANDS)
        return delegate.get(name)
    }
}

class GuardedMessaging(
    private val delegate: Messaging,
    private val guard: ModCapabilityGuard,
) : Messaging {
    override fun registerChannel(id: String, handler: MessageHandler): Channel {
        guard.require(ModCapability.MESSAGING)
        ensurePublicChannel(id)
        return GuardedChannel(delegate.registerChannel(id, handler), guard)
    }

    override fun unregisterChannel(id: String) {
        guard.require(ModCapability.MESSAGING)
        ensurePublicChannel(id)
        delegate.unregisterChannel(id)
    }

    override fun channel(id: String): Channel? {
        guard.require(ModCapability.MESSAGING)
        ensurePublicChannel(id)
        return delegate.channel(id)?.let { GuardedChannel(it, guard) }
    }

    private fun ensurePublicChannel(id: String) {
        require(id.isNotBlank()) { "Channel id must not be blank" }
        if (id.startsWith(INTERNAL_CHANNEL_PREFIX)) {
            throw IllegalArgumentException("Channel id is reserved: $id")
        }
    }

    private class GuardedChannel(
        private val delegate: Channel,
        private val guard: ModCapabilityGuard,
    ) : Channel {
        override val id: String
            get() = delegate.id

        override fun send(payload: ByteArray) {
            guard.require(ModCapability.MESSAGING)
            delegate.send(payload)
        }
    }

    private companion object {
        const val INTERNAL_CHANNEL_PREFIX = "lineage."
    }
}

class GuardedPlayerManager(
    private val delegate: PlayerManager,
    private val guard: ModCapabilityGuard,
) : PlayerManager {
    override fun get(id: UUID): ProxyPlayer? {
        guard.require(ModCapability.PLAYERS)
        return delegate.get(id)
    }

    override fun getByName(username: String): ProxyPlayer? {
        guard.require(ModCapability.PLAYERS)
        return delegate.getByName(username)
    }

    override fun all(): Collection<ProxyPlayer> {
        guard.require(ModCapability.PLAYERS)
        return delegate.all()
    }
}

class GuardedBackendRegistry(
    private val delegate: BackendRegistry,
    private val guard: ModCapabilityGuard,
) : BackendRegistry {
    override fun get(id: String): BackendInfo? {
        guard.require(ModCapability.BACKENDS)
        return delegate.get(id)
    }

    override fun all(): Collection<BackendInfo> {
        guard.require(ModCapability.BACKENDS)
        return delegate.all()
    }
}

class GuardedPermissionChecker(
    private val delegate: PermissionChecker,
    private val guard: ModCapabilityGuard,
) : PermissionChecker {
    override fun hasPermission(subject: PermissionSubject, permission: String): Boolean {
        guard.require(ModCapability.PERMISSIONS)
        return delegate.hasPermission(subject, permission)
    }
}

class GuardedScheduler(
    private val delegate: Scheduler,
    private val guard: ModCapabilityGuard,
) : Scheduler {
    override fun runSync(task: Runnable): TaskHandle {
        guard.require(ModCapability.SCHEDULER)
        return delegate.runSync(task)
    }

    override fun runAsync(task: Runnable): TaskHandle {
        guard.require(ModCapability.SCHEDULER)
        return delegate.runAsync(task)
    }

    override fun runLater(delay: Duration, task: Runnable): TaskHandle {
        guard.require(ModCapability.SCHEDULER)
        return delegate.runLater(delay, task)
    }

    override fun runRepeating(interval: Duration, task: Runnable): TaskHandle {
        guard.require(ModCapability.SCHEDULER)
        return delegate.runRepeating(interval, task)
    }

    override fun runRepeating(delay: Duration, interval: Duration, task: Runnable): TaskHandle {
        guard.require(ModCapability.SCHEDULER)
        return delegate.runRepeating(delay, interval, task)
    }
}

class GuardedServiceRegistry(
    private val delegate: ServiceRegistry,
    private val guard: ModCapabilityGuard,
) : ServiceRegistry {
    override fun <T : Any> register(key: ServiceKey<T>, service: T) {
        guard.require(ModCapability.SERVICES)
        ensurePublicServiceKey(key)
        delegate.register(key, service)
    }

    override fun <T : Any> get(key: ServiceKey<T>): T? {
        guard.require(ModCapability.SERVICES)
        ensurePublicServiceKey(key)
        return delegate.get(key)
    }

    override fun <T : Any> unregister(key: ServiceKey<T>) {
        guard.require(ModCapability.SERVICES)
        ensurePublicServiceKey(key)
        delegate.unregister(key)
    }

    override fun keys(): Set<ServiceKey<*>> {
        guard.require(ModCapability.SERVICES)
        return delegate.keys()
    }

    private fun ensurePublicServiceKey(key: ServiceKey<*>) {
        val typeName = key.type.name
        if (RESERVED_SERVICE_TYPE_PREFIXES.any { typeName.startsWith(it) }) {
            throw IllegalArgumentException("Service key is reserved: $typeName")
        }
        if (key.name.startsWith(RESERVED_SERVICE_NAME_PREFIX)) {
            throw IllegalArgumentException("Service key name is reserved: ${key.name}")
        }
    }

    private companion object {
        val RESERVED_SERVICE_TYPE_PREFIXES = listOf(
            "ru.hytalemodding.lineage.proxy.security.",
            "ru.hytalemodding.lineage.backend.security.",
            "ru.hytalemodding.lineage.shared.security.",
        )
        const val RESERVED_SERVICE_NAME_PREFIX = "lineage.internal."
    }
}
