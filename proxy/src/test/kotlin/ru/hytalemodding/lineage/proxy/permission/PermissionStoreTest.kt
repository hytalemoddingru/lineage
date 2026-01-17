/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.permission

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PermissionStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun savesAndLoadsPermissions() {
        val path = tempDir.resolve("permissions.toml")
        val store = PermissionStore(path)
        val data = mapOf(
            "player" to setOf("lineage.command.mod", "lineage.command.perm"),
        )

        store.save(data)
        val loaded = store.load()

        assertEquals(data, loaded)
    }

    @Test
    fun ignoresMissingFile() {
        val store = PermissionStore(tempDir.resolve("missing.toml"))
        val loaded = store.load()
        assertTrue(loaded.isEmpty())
    }
}
