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
import ru.hytalemodding.lineage.api.mod.ModInfo
import java.nio.file.Path

class ModDependencyResolverTest {
    @Test
    fun ordersDependenciesBeforeDependents() {
        val core = descriptor("core", "1.0.0")
        val feature = descriptor("feature", "1.0.0", dependencies = listOf("core"))
        val addon = descriptor("addon", "1.0.0", dependencies = listOf("feature"))

        val ordered = ModDependencyResolver.resolve(listOf(addon, core, feature))

        assertEquals(listOf("core", "feature", "addon"), ordered.map { it.info.id })
    }

    @Test
    fun rejectsMissingDependencies() {
        val addon = descriptor("addon", "1.0.0", dependencies = listOf("missing"))
        assertThrows(ModLoadException::class.java) {
            ModDependencyResolver.resolve(listOf(addon))
        }
    }

    @Test
    fun rejectsVersionMismatch() {
        val core = descriptor("core", "1.0.0")
        val addon = descriptor("addon", "1.0.0", dependencies = listOf("core>=2.0.0"))
        assertThrows(ModLoadException::class.java) {
            ModDependencyResolver.resolve(listOf(core, addon))
        }
    }

    @Test
    fun rejectsCycles() {
        val a = descriptor("a", "1.0.0", dependencies = listOf("b"))
        val b = descriptor("b", "1.0.0", dependencies = listOf("a"))
        assertThrows(ModLoadException::class.java) {
            ModDependencyResolver.resolve(listOf(a, b))
        }
    }

    private fun descriptor(
        id: String,
        version: String,
        dependencies: List<String> = emptyList(),
        softDependencies: List<String> = emptyList(),
    ): ModDescriptor {
        val info = ModInfo(
            id = id,
            name = id,
            version = version,
            apiVersion = "1.0.0",
            authors = listOf("dev"),
            description = "",
            dependencies = dependencies,
            softDependencies = softDependencies,
            website = null,
            license = null,
        )
        return ModDescriptor(info, "test.Main", Path.of("$id.jar"))
    }
}
