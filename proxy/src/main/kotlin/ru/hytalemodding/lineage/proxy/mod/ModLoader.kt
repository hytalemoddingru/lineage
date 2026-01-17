/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import java.nio.file.Files
import java.nio.file.Path

/**
 * Discovers mod jars and reads their metadata.
 */
class ModLoader(
    private val modsDirectory: Path,
) {
    fun discover(): List<ModDescriptor> {
        if (!Files.exists(modsDirectory)) {
            return emptyList()
        }
        if (!Files.isDirectory(modsDirectory)) {
            throw ModLoadException("Mods path is not a directory: $modsDirectory")
        }

        val descriptors = Files.list(modsDirectory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".jar") }
                .map { ModMetadataReader.read(it) }
                .toList()
        }

        val byId = mutableMapOf<String, ModDescriptor>()
        for (descriptor in descriptors) {
            ModMetadataValidator.validate(descriptor.info)
            val existing = byId.putIfAbsent(descriptor.info.id, descriptor)
            if (existing != null) {
                throw ModLoadException(
                    "Duplicate mod id ${descriptor.info.id} in ${descriptor.sourcePath} and ${existing.sourcePath}",
                )
            }
        }
        return descriptors
    }
}
