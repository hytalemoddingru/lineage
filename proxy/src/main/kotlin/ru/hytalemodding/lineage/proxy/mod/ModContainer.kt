/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import ru.hytalemodding.lineage.api.mod.LineageMod
import ru.hytalemodding.lineage.api.mod.ModInfo

/**
 * Holds the runtime state for a loaded mod.
 */
data class ModContainer(
    val info: ModInfo,
    val sourcePath: java.nio.file.Path,
    val instance: LineageMod,
    val classLoader: ModClassLoader,
    var state: ModState = ModState.LOADED,
)
