/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class RenderStyleLoaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun createsDefaultStyleFile() {
        val stylePath = tempDir.resolve("styles").resolve("rendering.toml")
        val limits = RenderStyleLoader.load(stylePath)
        assertTrue(Files.exists(stylePath))
        assertEquals(RenderLimits(), limits)
    }

    @Test
    fun loadsCustomLimits() {
        val stylePath = tempDir.resolve("styles").resolve("rendering.toml")
        Files.createDirectories(stylePath.parent)
        Files.writeString(
            stylePath,
            """
            max_input_length = 2048
            max_nesting_depth = 8
            max_gradient_chars = 128
            max_tag_length = 64
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        val limits = RenderStyleLoader.load(stylePath)
        assertEquals(2048, limits.maxInputLength)
        assertEquals(8, limits.maxNestingDepth)
        assertEquals(128, limits.maxGradientChars)
        assertEquals(64, limits.maxTagLength)
    }
}
