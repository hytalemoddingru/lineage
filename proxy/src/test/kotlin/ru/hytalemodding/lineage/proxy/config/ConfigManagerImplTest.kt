/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class ConfigManagerImplTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun resolvesConfigPaths() {
        val path = ConfigPathUtil.resolve(tempDir, "nested/config")
        assertEquals(tempDir.resolve("nested").resolve("config.toml"), path)

        val direct = ConfigPathUtil.resolve(tempDir, "direct.toml")
        assertEquals(tempDir.resolve("direct.toml"), direct)
    }

    @Test
    fun rejectsInvalidConfigPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigPathUtil.resolve(tempDir, "../bad")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigPathUtil.resolve(tempDir, "/absolute")
        }
    }

    @Test
    fun loadsAndSavesConfigValues() {
        val manager = ConfigManagerImpl(tempDir)
        val config = manager.config("settings", createIfMissing = true) {
            "title = \"hello\""
        }
        assertTrue(Files.exists(tempDir.resolve("settings.toml")))
        assertEquals("hello", config.getString("title"))

        config.set("flags.enabled", true)
        config.set("count", 3)
        config.set("timeout", Duration.ofSeconds(5))
        config.save()

        val reloaded = ConfigManagerImpl(tempDir).config("settings")
        assertEquals(true, reloaded.getBoolean("flags.enabled"))
        assertEquals(3, reloaded.getInt("count"))
        assertEquals(Duration.ofSeconds(5), reloaded.getDuration("timeout"))
    }
}
