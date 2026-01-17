/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.config

import java.nio.file.Path

/**
 * Resolves configuration paths for mods.
 */
object ConfigPathUtil {
    fun resolve(baseDir: Path, name: String): Path {
        val normalized = name.replace('\\', '/').trim()
        if (normalized.isBlank()) {
            throw IllegalArgumentException("Config name must not be blank")
        }
        if (normalized.startsWith("/") || normalized.startsWith("~")) {
            throw IllegalArgumentException("Config name must be relative: $name")
        }
        if (normalized.contains("..")) {
            throw IllegalArgumentException("Config name must not contain '..': $name")
        }
        val resolved = baseDir.resolve(normalized)
        return if (resolved.fileName.toString().contains('.')) {
            resolved
        } else {
            resolved.resolveSibling(resolved.fileName.toString() + ".toml")
        }
    }
}
