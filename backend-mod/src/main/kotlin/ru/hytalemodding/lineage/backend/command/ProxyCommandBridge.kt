/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.command

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import org.slf4j.LoggerFactory
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import ru.hytalemodding.lineage.shared.control.ControlReplayProtector
import ru.hytalemodding.lineage.shared.command.ProxyCommandDescriptor
import ru.hytalemodding.lineage.shared.command.ProxyCommandFlags
import ru.hytalemodding.lineage.shared.command.ProxyCommandRegistryProtocol
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ProxyCommandBridge internal constructor(
    private val isMessagingEnabled: () -> Boolean,
    private val commandRegistrar: BackendBridgeCommandRegistrar,
    private val messaging: BackendBridgeMessaging = DefaultBackendBridgeMessaging,
    private val clock: Clock = SystemClock,
    private val syncRetryIntervalMillis: Long = SYNC_RETRY_INTERVAL_MILLIS,
    private val schedulerFactory: () -> ScheduledExecutorService = {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "lineage-command-sync").apply { isDaemon = true }
        }
    },
) {
    constructor(
        plugin: JavaPlugin,
        isMessagingEnabled: () -> Boolean,
        clock: Clock = SystemClock,
    ) : this(
        isMessagingEnabled = isMessagingEnabled,
        commandRegistrar = HytaleBackendBridgeCommandRegistrar(plugin),
        messaging = DefaultBackendBridgeMessaging,
        clock = clock,
    )

    private val logger = LoggerFactory.getLogger(ProxyCommandBridge::class.java)
    private val registrations = ArrayList<BackendBridgeRegistration>()
    @Volatile
    private var registrySynchronized = false
    @Volatile
    private var syncScheduler: ScheduledExecutorService? = null
    private val started = AtomicBoolean(false)
    private val replayProtector = ControlReplayProtector(
        windowMillis = 10_000L,
        maxEntries = 100_000,
        clock = clock,
    )

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        registrySynchronized = false
        messaging.registerChannel(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID, this::handleSnapshot)
        startSyncScheduler()
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) {
            return
        }
        syncScheduler?.shutdownNow()
        syncScheduler = null
        registrySynchronized = false
        messaging.unregisterChannel(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID)
        clearRegistrations()
    }

    fun requestSync() {
        registrySynchronized = false
        messaging.send(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, ProxyCommandRegistryProtocol.encodeRequest())
    }

    internal fun isSynchronizedForTests(): Boolean = registrySynchronized

    private fun handleSnapshot(payload: ByteArray) {
        val version = ProxyCommandRegistryProtocol.peekVersion(payload)
        if (version == null) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "command-registry-sync",
                    severity = "WARN",
                    reason = "MALFORMED_SNAPSHOT",
                )
            )
            return
        }
        if (!ProxyCommandRegistryProtocol.hasSupportedVersion(payload)) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "command-registry-sync",
                    severity = "WARN",
                    reason = "VERSION_MISMATCH",
                    fields = mapOf("version" to version),
                )
            )
            return
        }
        val snapshot = ProxyCommandRegistryProtocol.decodeSnapshot(payload) ?: run {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "command-registry-sync",
                    severity = "WARN",
                    reason = "MALFORMED_SNAPSHOT",
                )
            )
            return
        }
        val correlationId = snapshotCorrelationId(snapshot.senderId, snapshot.nonce)
        if (snapshot.senderId != EXPECTED_SNAPSHOT_SENDER_ID) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "command-registry-sync",
                    severity = "WARN",
                    reason = "UNEXPECTED_SENDER",
                    correlationId = correlationId,
                    fields = mapOf("senderId" to snapshot.senderId),
                )
            )
            return
        }
        if (!ControlProtocol.isTimestampValid(
                snapshot.issuedAtMillis,
                snapshot.ttlMillis,
                clock.nowMillis(),
            )
        ) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "command-registry-sync",
                    severity = "WARN",
                    reason = "INVALID_TIMESTAMP",
                    correlationId = correlationId,
                )
            )
            return
        }
        if (!replayProtector.tryRegister(snapshot.senderId, ControlMessageType.TRANSFER_RESULT, snapshot.nonce)) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "command-registry-sync",
                    severity = "WARN",
                    reason = "REPLAYED_SNAPSHOT",
                    correlationId = correlationId,
                )
            )
            return
        }
        applySnapshot(snapshot.commands)
        registrySynchronized = true
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
        if (!commandRegistrar.isNameAvailable(name)) {
            logger.warn("Command /{} is already registered; skipping.", name)
            return
        }
        val registration = commandRegistrar.register(
            BackendBridgeCommandDefinition(
                name = name,
                aliases = aliases,
                description = description,
                usage = usage,
                flags = flags,
                isMessagingEnabled = isMessagingEnabled,
                isRegistrySynchronized = { registrySynchronized },
            )
        )
        if (registration != null) {
            registrations.add(registration)
        }
    }

    private fun isNameAvailable(name: String): Boolean {
        return commandRegistrar.isNameAvailable(name)
    }

    private fun clearRegistrations() {
        for (registration in registrations) {
            registration.unregister()
        }
        registrations.clear()
    }

    private fun snapshotCorrelationId(senderId: String, nonce: ByteArray): String {
        val nonceB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
        return "$senderId:$nonceB64"
    }

    private fun startSyncScheduler() {
        requestSync()
        val scheduler = schedulerFactory().also { syncScheduler = it }
        scheduler.scheduleAtFixedRate(
            {
                if (!registrySynchronized) {
                    messaging.send(
                        ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID,
                        ProxyCommandRegistryProtocol.encodeRequest(),
                    )
                }
            },
            syncRetryIntervalMillis,
            syncRetryIntervalMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    private companion object {
        private const val EXPECTED_SNAPSHOT_SENDER_ID = "proxy"
        private const val SYNC_RETRY_INTERVAL_MILLIS = 2_000L
    }
}
