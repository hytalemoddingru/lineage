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
import ru.hytalemodding.lineage.proxy.console.StartupBanner
import ru.hytalemodding.lineage.proxy.command.CommandDispatcher
import ru.hytalemodding.lineage.proxy.command.CommandRegistryImpl
import ru.hytalemodding.lineage.proxy.command.CommandRegistrySyncService
import ru.hytalemodding.lineage.proxy.command.ConsoleCommandSender
import ru.hytalemodding.lineage.proxy.command.HelpCommand
import ru.hytalemodding.lineage.proxy.command.ListPlayersCommand
import ru.hytalemodding.lineage.proxy.command.ModCommand
import ru.hytalemodding.lineage.proxy.command.MessagesCommand
import ru.hytalemodding.lineage.proxy.command.PingCommand
import ru.hytalemodding.lineage.proxy.command.PlayerCommandGateway
import ru.hytalemodding.lineage.proxy.command.PlayerInfoCommand
import ru.hytalemodding.lineage.proxy.command.PermissionCommand
import ru.hytalemodding.lineage.proxy.command.StopCommand
import ru.hytalemodding.lineage.proxy.command.TransferCommand
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl
import ru.hytalemodding.lineage.proxy.permission.PermissionStore
import ru.hytalemodding.lineage.api.routing.RoutingStrategy
import ru.hytalemodding.lineage.proxy.routing.StaticRoutingStrategy
import ru.hytalemodding.lineage.proxy.routing.StrategyRouter
import ru.hytalemodding.lineage.proxy.routing.EventRouter
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
import ru.hytalemodding.lineage.proxy.player.PlayerTransferService
import ru.hytalemodding.lineage.proxy.player.ProxySystemMessageFormatter
import ru.hytalemodding.lineage.proxy.control.ControlPlaneService
import ru.hytalemodding.lineage.proxy.schedule.SchedulerImpl
import ru.hytalemodding.lineage.proxy.service.ServiceRegistryImpl
import ru.hytalemodding.lineage.proxy.security.TransferTokenIssuer
import ru.hytalemodding.lineage.proxy.observability.HealthHttpServer
import ru.hytalemodding.lineage.proxy.observability.ProxyHealthEvaluator
import ru.hytalemodding.lineage.proxy.observability.ProxyMetricsRegistry
import ru.hytalemodding.lineage.proxy.i18n.LocalizationRuntime
import ru.hytalemodding.lineage.proxy.i18n.ProxyLocalizationService
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityTracker
import ru.hytalemodding.lineage.proxy.net.BackendCertificatePolicyStore
import ru.hytalemodding.lineage.proxy.text.ProxyTextRendererService
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.proxy.logging.ProxyLogArchiveService
import ru.hytalemodding.lineage.api.i18n.LocalizationService
import ru.hytalemodding.lineage.api.text.TextRendererService
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * Proxy entry point. Loads configuration and starts the Netty listener.
 */
fun main(args: Array<String>) {
    val proxyVersion = ProxyRuntimeInfo.version
    val options = ProxyBootstrapOptionsParser.parse(args)
    val configPath = options.configPath
    val runtimeDir = configPath.parent ?: Path.of(".")
    val config = TomlLoader.load(configPath)
    val localizationRuntime = LocalizationRuntime(
        messagesDir = runtimeDir.resolve("messages"),
        stylePath = runtimeDir.resolve("styles").resolve("rendering.toml"),
    )
    val messages = localizationRuntime.messages
    val renderLimitsProvider = { localizationRuntime.renderLimits() }
    runCatching {
        ProxyLogArchiveService.archiveAndPrune(
            logsDir = runtimeDir.resolve("logs"),
            maxArchives = config.logging.maxArchiveFiles,
        )
    }.onFailure { error ->
        System.err.println("Failed to archive proxy logs: ${error.message}")
    }
    System.setProperty("lineage.log.level", if (config.logging.debug) "DEBUG" else "INFO")
    val logger = Logging.logger(ProxyListener::class.java)
    StartupBanner.print(proxyVersion)
    if (options.strictMode) {
        enforceStrictMode(config)
    }
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
    val permissionsPath = runtimeDir.resolve("permissions.toml")
    val permissionStore = PermissionStore(permissionsPath)
    val permissionChecker = PermissionCheckerImpl().also {
        it.load(permissionStore.load())
    }
    val dispatcher = CommandDispatcher(commandRegistry, permissionChecker, messages)
    val consoleInput = ConsoleInputService(
        dispatcher = dispatcher,
        sender = ConsoleCommandSender(renderLimitsProvider),
        historyPath = runtimeDir.resolve(".consolehistory"),
        prompt = buildConsolePrompt(),
        historyLimit = config.console.historyLimit,
    )
    val serviceRegistry = ServiceRegistryImpl()
    serviceRegistry.register(LocalizationService.SERVICE_KEY, ProxyLocalizationService(messages))
    serviceRegistry.register(TextRendererService.SERVICE_KEY, ProxyTextRendererService(renderLimitsProvider))
    val routingStrategy = StaticRoutingStrategy(config)
    serviceRegistry.register(RoutingStrategy.SERVICE_KEY, routingStrategy)
    val metrics = ProxyMetricsRegistry()
    val baseRouter = StrategyRouter(config, serviceRegistry, routingStrategy)
    val router = EventRouter(baseRouter, eventBus, metrics, rateLimitService.routingInFlight)
    val scheduler = SchedulerImpl()
    val listenerChannelRef = AtomicReference<io.netty.channel.Channel?>()
    val transferServiceRef = AtomicReference<PlayerTransferService?>(null)
    val players = PlayerManagerImpl { transferServiceRef.get() }
    metrics.bindSessionsGauge { sessionManager.size() }
    metrics.bindPlayersGauge { players.all().size }
    metrics.bindMessagingEnabledGauge { config.messaging.enabled }
    metrics.bindMessagingRunningGauge { messagingServer?.isRunning() == true }
    val backends = BackendRegistryImpl(config)
    val certificatePolicyStore = BackendCertificatePolicyStore(config.backends)
    val backendAvailabilityTracker = BackendAvailabilityTracker(
        knownBackendIds = config.backends.map { it.id }.toSet(),
    )
    val modsDir = runtimeDir.resolve("mods")
    var messaging: ru.hytalemodding.lineage.api.messaging.Messaging = NoopMessaging()
    var controlPlane: ControlPlaneService? = null
    var commandSync: CommandRegistrySyncService? = null
    var listenerActive = false

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
        val allowedBackendSenderIds = config.backends.map { it.id }.toSet()
        controlPlane = ControlPlaneService(
            messaging = messaging,
            config = config.messaging,
            eventBus = eventBus,
            players = players,
            backendAvailabilityTracker = backendAvailabilityTracker,
            allowedBackendSenderIds = allowedBackendSenderIds,
            metrics = metrics,
        )
        commandSync = CommandRegistrySyncService(commandRegistry, messaging)
        logger.info("Messaging UDP enabled on {}:{}", config.messaging.host, config.messaging.port)
    }
    val transferTokenIssuer = TransferTokenIssuer(secret)
    val transferService = PlayerTransferService(
        eventBus = eventBus,
        requestSender = controlPlane,
        tokenIssuer = transferTokenIssuer,
        knownBackendIds = config.backends.map { it.id },
        backendAvailabilityTracker = backendAvailabilityTracker,
    )
    transferServiceRef.set(transferService)
    val playerSystemChannel = messaging.channel(PlayerCommandProtocol.SYSTEM_RESPONSE_CHANNEL_ID)
        ?: messaging.registerChannel(PlayerCommandProtocol.SYSTEM_RESPONSE_CHANNEL_ID, MessageHandler { })
    players.setMessageSender { player, message ->
        if (!config.messaging.enabled) {
            return@setMessageSender false
        }
        val formatted = ProxySystemMessageFormatter.format(
            player.language,
            message,
            messages,
            renderLimitsProvider(),
        )
        val payload = PlayerCommandProtocol.encodeResponse(player.id, formatted)
        playerSystemChannel.send(payload)
        true
    }
    val liveListener = ProxyListener(
        config,
        router,
        sessionManager,
        tokenService,
        transferTokenValidator,
        rateLimitService,
        players,
        eventBus,
        transferService,
        certificatePolicyStore,
        backendAvailabilityTracker,
        metrics,
    )
    PlayerCommandGateway(
        messaging = messaging,
        dispatcher = dispatcher,
        players = players,
        messages = messages,
        replayWindowMillis = config.messaging.controlReplayWindowMillis,
        replayMaxEntries = config.messaging.controlReplayMaxEntries,
        maxSkewMillis = config.messaging.controlMaxSkewMillis,
        renderLimitsProvider = renderLimitsProvider,
    )
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
    commandRegistry.register(ModCommand(modManager, messages))
    commandRegistry.register(MessagesCommand(localizationRuntime, messages))
    commandRegistry.register(HelpCommand(commandRegistry, permissionChecker, messages))
    commandRegistry.register(ListPlayersCommand(players, config.backends.map { it.id }, messages))
    commandRegistry.register(PlayerInfoCommand(players, messages))
    commandRegistry.register(PingCommand(players, messages))
    commandRegistry.register(PermissionCommand(permissionChecker, players, permissionStore, messages))
    commandRegistry.register(
        StopCommand(
            requestShutdown = {
                val listenerChannel = listenerChannelRef.get()
                if (listenerChannel != null && listenerChannel.isOpen) {
                    listenerChannel.close()
                } else {
                    logger.info("Shutdown requested before listener channel was active")
                }
            },
            messages = messages,
        )
    )
    commandRegistry.register(
        TransferCommand(
            players = players,
            transferService = transferService,
            backends = config.backends,
            availabilityTracker = backendAvailabilityTracker,
            messages = messages,
        )
    )
    modManager.loadAll()
    modManager.enableAll()
    commandSync?.sendSnapshot()
    val channel = liveListener.start()
    listenerChannelRef.set(channel)
    listenerActive = true
    val healthEvaluator = ProxyHealthEvaluator(
        listenerActive = { listenerActive && channel.isActive },
        messagingEnabled = config.messaging.enabled,
        messagingActive = { messagingServer?.isRunning() == true },
    )
    val healthServer = HealthHttpServer(
        config = config.observability,
        evaluator = healthEvaluator,
        metricsProvider = { metrics.renderPrometheus() },
        statusProvider = {
            renderRuntimeStatusJson(
                healthStatus = healthEvaluator.snapshot().status.name,
                listenerActive = listenerActive && channel.isActive,
                messagingEnabled = config.messaging.enabled,
                messagingRunning = messagingServer?.isRunning() == true,
                sessions = sessionManager.size(),
                players = players.all().size,
                controlRejectCounters = controlPlane?.rejectCountersSnapshot() ?: emptyMap(),
            )
        },
    )
    healthServer.start()
    consoleInput.start()

    channel.closeFuture().syncUninterruptibly()
    listenerActive = false
    consoleInput.close()
    healthServer.close()
    modManager.unloadAll()
    scheduler.shutdown()
    messagingServer?.close()
    liveListener.close()
}

private fun renderRuntimeStatusJson(
    healthStatus: String,
    listenerActive: Boolean,
    messagingEnabled: Boolean,
    messagingRunning: Boolean,
    sessions: Int,
    players: Int,
    controlRejectCounters: Map<String, Long>,
): String {
    val rejects = controlRejectCounters.entries
        .sortedBy { it.key }
        .joinToString(",") { entry ->
            "\"${escapeJson(entry.key)}\":${entry.value}"
        }
    return """
        {"health":"$healthStatus","listener":{"active":$listenerActive},"messaging":{"enabled":$messagingEnabled,"running":$messagingRunning},"sessions":{"active":$sessions},"players":{"active":$players},"controlPlane":{"rejectCounters":{$rejects}}}
    """.trimIndent()
}

private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

private fun enforceStrictMode(config: ru.hytalemodding.lineage.proxy.config.ProxyConfig) {
    require(config.messaging.enabled) {
        "Strict mode requires [messaging].enabled = true"
    }
    require(config.observability.enabled) {
        "Strict mode requires [observability].enabled = true"
    }
}

private fun buildConsolePrompt(): String {
    val reset = "\u001B[0m"
    val lineage = "\u001B[1;35m"
    val at = "\u001B[2;37m"
    val target = "\u001B[37m"
    val arrow = "\u001B[1;37m"
    return "${lineage}lineage${reset}${at}@${reset}${target}proxy${reset} ${arrow}»${reset} "
}
