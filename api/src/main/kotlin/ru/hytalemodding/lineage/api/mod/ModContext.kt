/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.mod

import org.slf4j.Logger
import ru.hytalemodding.lineage.api.backend.BackendRegistry
import ru.hytalemodding.lineage.api.command.CommandRegistry
import ru.hytalemodding.lineage.api.config.ConfigManager
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.permission.PermissionChecker
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.api.schedule.Scheduler
import ru.hytalemodding.lineage.api.service.ServiceRegistry
import java.nio.file.Path

/**
 * Provides mod-scoped access to core proxy services.
 */
interface ModContext {
    val modInfo: ModInfo
    val logger: Logger
    val dataDirectory: Path
    val configManager: ConfigManager
    val eventBus: EventBus
    val commandRegistry: CommandRegistry
    val scheduler: Scheduler
    val messaging: Messaging
    val players: PlayerManager
    val backends: BackendRegistry
    val permissionChecker: PermissionChecker
    val serviceRegistry: ServiceRegistry
}
