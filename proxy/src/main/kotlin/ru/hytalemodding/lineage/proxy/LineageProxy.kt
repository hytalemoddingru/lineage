/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy

import ru.hytalemodding.lineage.proxy.config.TomlLoader
import ru.hytalemodding.lineage.proxy.net.ProxyListener
import ru.hytalemodding.lineage.proxy.console.ConsoleInputService
import ru.hytalemodding.lineage.proxy.command.CommandDispatcher
import ru.hytalemodding.lineage.proxy.command.CommandRegistryImpl
import ru.hytalemodding.lineage.proxy.command.ConsoleCommandSender
import ru.hytalemodding.lineage.proxy.command.ModCommand
import ru.hytalemodding.lineage.proxy.command.PlayerCommandGateway
import ru.hytalemodding.lineage.proxy.command.PermissionCommand
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl
import ru.hytalemodding.lineage.proxy.permission.PermissionStore
import ru.hytalemodding.lineage.api.routing.RoutingStrategy
import ru.hytalemodding.lineage.proxy.routing.StaticRoutingStrategy
import ru.hytalemodding.lineage.proxy.routing.StrategyRouter
import ru.hytalemodding.lineage.proxy.security.TokenService
import ru.hytalemodding.lineage.proxy.security.TransferTokenValidator
import ru.hytalemodding.lineage.proxy.security.RateLimitService
import ru.hytalemodding.lineage.proxy.session.SessionManager
import ru.hytalemodding.lineage.proxy.util.Logging
import ru.hytalemodding.lineage.proxy.messaging.MessagingImpl
import ru.hytalemodding.lineage.proxy.messaging.MessagingServer
import ru.hytalemodding.lineage.proxy.messaging.NoopMessaging
import ru.hytalemodding.lineage.proxy.event.EventBusImpl
import ru.hytalemodding.lineage.proxy.mod.ModContextFactory
import ru.hytalemodding.lineage.proxy.mod.ModManager
import ru.hytalemodding.lineage.proxy.backend.BackendRegistryImpl
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.schedule.SchedulerImpl
import ru.hytalemodding.lineage.proxy.service.ServiceRegistryImpl
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.net.InetSocketAddress

/**
 * Proxy entry point. Loads configuration and starts the Netty listener.
 */
fun main(args: Array<String>) {
    val logger = Logging.logger(ProxyListener::class.java)
    val configPath = Path.of(args.firstOrNull() ?: "config.toml")
    val config = TomlLoader.load(configPath)
    val sessionManager = SessionManager()
    val secret = config.security.proxySecret.toByteArray(StandardCharsets.UTF_8)
    val tokenService = TokenService(
        secret,
        config.security.tokenTtlMillis,
    )
    val transferTokenValidator = TransferTokenValidator(secret)
    val rateLimitService = RateLimitService(config.rateLimits)
    var messagingServer: MessagingServer? = null
    val eventBus = EventBusImpl()
    val commandRegistry = CommandRegistryImpl()
    val permissionsPath = (configPath.parent ?: Path.of(".")).resolve("permissions.toml")
    val permissionStore = PermissionStore(permissionsPath)
    val permissionChecker = PermissionCheckerImpl().also {
        it.load(permissionStore.load())
    }
    val dispatcher = CommandDispatcher(commandRegistry, permissionChecker)
    val consoleInput = ConsoleInputService(dispatcher, ConsoleCommandSender())
    val serviceRegistry = ServiceRegistryImpl()
    val routingStrategy = StaticRoutingStrategy(config)
    serviceRegistry.register(RoutingStrategy.SERVICE_KEY, routingStrategy)
    val router = StrategyRouter(config, serviceRegistry, routingStrategy)
    val scheduler = SchedulerImpl()
    val players = PlayerManagerImpl()
    val backends = BackendRegistryImpl(config)
    val modsDir = (configPath.parent ?: Path.of(".")).resolve("mods")
    var messaging: ru.hytalemodding.lineage.api.messaging.Messaging = NoopMessaging()
    val listener = ProxyListener(
        config,
        router,
        sessionManager,
        tokenService,
        transferTokenValidator,
        rateLimitService,
        players,
        eventBus,
    )

    logger.info("Starting Lineage proxy with config {}", configPath.toAbsolutePath())
    if (config.messaging.enabled) {
        val address = InetSocketAddress(config.messaging.host, config.messaging.port)
        lateinit var impl: MessagingImpl
        val server = MessagingServer(address, secret) { from, channelId, payload ->
            impl.onPacket(from, channelId, payload)
        }
        impl = MessagingImpl(server)
        impl.start()
        messagingServer = server
        messaging = impl
        logger.info("Messaging UDP enabled on {}:{}", config.messaging.host, config.messaging.port)
    }
    PlayerCommandGateway(messaging, dispatcher, players)
    val contextFactory = ModContextFactory(
        modsDirectory = modsDir,
        eventBus = eventBus,
        commandRegistry = commandRegistry,
        scheduler = scheduler,
        messaging = messaging,
        players = players,
        backends = backends,
        permissionChecker = permissionChecker,
        serviceRegistry = serviceRegistry,
    )
    val modManager = ModManager(modsDir, contextFactory::create)
    commandRegistry.register(ModCommand(modManager))
    commandRegistry.register(PermissionCommand(permissionChecker, players, permissionStore))
    modManager.loadAll()
    modManager.enableAll()
    val channel = listener.start()
    consoleInput.start()

    channel.closeFuture().syncUninterruptibly()
    consoleInput.close()
    modManager.unloadAll()
    scheduler.shutdown()
    messagingServer?.close()
    listener.close()
}
