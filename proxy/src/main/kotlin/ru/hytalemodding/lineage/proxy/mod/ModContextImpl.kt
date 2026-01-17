/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import org.slf4j.Logger
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
import java.nio.file.Path

/**
 * Default mod context implementation.
 */
class ModContextImpl(
    override val modInfo: ModInfo,
    override val logger: Logger,
    override val dataDirectory: Path,
    override val configManager: ConfigManager,
    override val eventBus: EventBus,
    override val commandRegistry: CommandRegistry,
    override val scheduler: Scheduler,
    override val messaging: Messaging,
    override val players: PlayerManager,
    override val backends: BackendRegistry,
    override val permissionChecker: PermissionChecker,
    override val serviceRegistry: ServiceRegistry,
) : ModContext
