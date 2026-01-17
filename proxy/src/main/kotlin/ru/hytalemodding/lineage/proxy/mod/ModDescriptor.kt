/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import ru.hytalemodding.lineage.api.mod.ModInfo
import java.nio.file.Path

/**
 * Describes a discovered mod and its origin.
 */
data class ModDescriptor(
    val info: ModInfo,
    val mainClassName: String,
    val sourcePath: Path,
)
