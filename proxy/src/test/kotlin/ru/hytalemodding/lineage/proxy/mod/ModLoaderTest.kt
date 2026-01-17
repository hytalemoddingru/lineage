/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.hytalemodding.lineage.api.mod.ModInfo
import java.nio.file.Path

class ModLoaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun discoversModMetadata() {
        val info = baseInfo(id = "demo")
        createModJar(tempDir, "ru.hytalemodding.lineage.test.DemoMod", info)

        val loader = ModLoader(tempDir)
        val mods = loader.discover()

        assertEquals(1, mods.size)
        assertEquals("demo", mods.first().info.id)
    }

    @Test
    fun rejectsDuplicateIds() {
        val info = baseInfo(id = "dupe")
        val first = createModJar(tempDir, "ru.hytalemodding.lineage.test.ModA", info)
        java.nio.file.Files.copy(first, tempDir.resolve("dupe-copy.jar"))

        val loader = ModLoader(tempDir)
        assertThrows(ModLoadException::class.java) {
            loader.discover()
        }
    }

    private fun baseInfo(
        id: String,
    ): ModInfo {
        return ModInfo(
            id = id,
            name = "Test Mod",
            version = "1.0.0",
            apiVersion = "1.0.0",
            authors = listOf("dev"),
            description = "",
            dependencies = emptyList(),
            softDependencies = emptyList(),
            website = null,
            license = null,
        )
    }
}
