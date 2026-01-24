/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.mod.ModInfo

class ModMetadataValidatorTest {
    @Test
    fun acceptsValidMetadata() {
        ModMetadataValidator.validate(baseInfo())
    }

    @Test
    fun rejectsInvalidId() {
        val info = baseInfo(id = "Bad!")
        assertThrows(ModLoadException::class.java) {
            ModMetadataValidator.validate(info)
        }
    }

    @Test
    fun rejectsInvalidName() {
        val info = baseInfo(name = " bad")
        assertThrows(ModLoadException::class.java) {
            ModMetadataValidator.validate(info)
        }
    }

    @Test
    fun rejectsInvalidVersion() {
        val info = baseInfo(version = "1.0")
        assertThrows(ModLoadException::class.java) {
            ModMetadataValidator.validate(info)
        }
    }

    @Test
    fun rejectsBlankAuthor() {
        val info = baseInfo(authors = listOf("dev", ""))
        assertThrows(ModLoadException::class.java) {
            ModMetadataValidator.validate(info)
        }
    }

    private fun baseInfo(
        id: String = "test_mod",
        name: String = "Test Mod",
        version: String = "1.0.0",
        apiVersion: String = "1.0.0",
        authors: List<String> = listOf("dev"),
    ): ModInfo {
        return ModInfo(
            id = id,
            name = name,
            version = version,
            apiVersion = apiVersion,
            authors = authors,
            description = "",
            dependencies = emptyList(),
            softDependencies = emptyList(),
            capabilities = emptySet(),
            website = null,
            license = null,
        )
    }
}
