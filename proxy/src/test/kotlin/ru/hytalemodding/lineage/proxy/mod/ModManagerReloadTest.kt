/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.hytalemodding.lineage.api.mod.ModInfo
import java.nio.file.Path

class ModManagerReloadTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun reloadAllInvokesDisableAndEnable() {
        val info = baseInfo("reloadable")
        createModJar(tempDir, "ru.hytalemodding.lineage.test.ReloadableMod", info)
        val manager = ModManager(tempDir) { modInfo ->
            createModContext(modInfo, tempDir)
        }

        TestHooks.reset()
        manager.loadAll()
        manager.enableAll()
        manager.reloadAll()

        assertEquals(
            listOf("enable:reloadable", "disable:reloadable", "enable:reloadable"),
            TestHooks.snapshot(),
        )
        manager.unloadAll()
    }

    private fun baseInfo(id: String): ModInfo {
        return ModInfo(
            id = id,
            name = "Reload Mod",
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
