/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.config

import ru.hytalemodding.lineage.api.config.ConfigManager
import ru.hytalemodding.lineage.api.config.ModConfig
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages mod configuration files within a data directory.
 */
class ConfigManagerImpl(
    private val dataDirectory: Path,
) : ConfigManager {
    private val configs = ConcurrentHashMap<String, ModConfig>()

    override fun config(name: String, createIfMissing: Boolean, defaults: (() -> String)?): ModConfig {
        val path = ConfigPathUtil.resolve(dataDirectory, name)
        val key = path.normalize().toString()
        return configs.computeIfAbsent(key) {
            ModConfigImpl(name, path, defaults, createIfMissing)
        }
    }

    override fun reloadAll() {
        configs.values.forEach { it.reload() }
    }

    override fun saveAll() {
        configs.values.forEach { it.save() }
    }
}
