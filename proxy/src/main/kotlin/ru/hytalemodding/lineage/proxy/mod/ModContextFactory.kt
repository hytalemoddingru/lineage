/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import org.slf4j.LoggerFactory
import ru.hytalemodding.lineage.api.backend.BackendRegistry
import ru.hytalemodding.lineage.api.command.CommandRegistry
import ru.hytalemodding.lineage.api.config.ConfigManager
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.mod.ModContext
import ru.hytalemodding.lineage.api.mod.ModInfo
import ru.hytalemodding.lineage.api.permission.PermissionChecker
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.api.schedule.Scheduler
import ru.hytalemodding.lineage.api.service.ServiceRegistry
import ru.hytalemodding.lineage.proxy.config.ConfigManagerImpl
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates mod contexts with shared runtime services.
 */
class ModContextFactory(
    private val modsDirectory: Path,
    private val eventBus: EventBus,
    private val commandRegistry: CommandRegistry,
    private val scheduler: Scheduler,
    private val messaging: Messaging,
    private val players: PlayerManager,
    private val backends: BackendRegistry,
    private val permissionChecker: PermissionChecker,
    private val serviceRegistry: ServiceRegistry,
) {
    fun create(modInfo: ModInfo): ModContext {
        val dataDirectory = modsDirectory.resolve(modInfo.id)
        Files.createDirectories(dataDirectory)
        val logger = LoggerFactory.getLogger("lineage.mod.${modInfo.id}")
        val configManager: ConfigManager = ConfigManagerImpl(dataDirectory)
        return ModContextImpl(
            modInfo = modInfo,
            logger = logger,
            dataDirectory = dataDirectory,
            configManager = configManager,
            eventBus = eventBus,
            commandRegistry = commandRegistry,
            scheduler = scheduler,
            messaging = messaging,
            players = players,
            backends = backends,
            permissionChecker = permissionChecker,
            serviceRegistry = serviceRegistry,
        )
    }
}
