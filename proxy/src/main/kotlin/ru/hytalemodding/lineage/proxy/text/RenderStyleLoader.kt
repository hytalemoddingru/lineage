/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.text

import org.tomlj.Toml
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads bounded text rendering limits from a TOML file.
 */
object RenderStyleLoader {
    fun load(stylePath: Path): RenderLimits {
        ensureDefaultFile(stylePath)
        Files.newBufferedReader(stylePath, StandardCharsets.UTF_8).use { reader ->
            val result = Toml.parse(reader)
            if (result.hasErrors()) {
                val errors = result.errors().joinToString("; ") { it.toString() }
                throw IllegalStateException("Failed to parse ${stylePath.fileName}: $errors")
            }
            val maxInputLength = result.getLong("max_input_length")?.toInt() ?: DEFAULT_LIMITS.maxInputLength
            val maxNestingDepth = result.getLong("max_nesting_depth")?.toInt() ?: DEFAULT_LIMITS.maxNestingDepth
            val maxGradientChars = result.getLong("max_gradient_chars")?.toInt() ?: DEFAULT_LIMITS.maxGradientChars
            val maxTagLength = result.getLong("max_tag_length")?.toInt() ?: DEFAULT_LIMITS.maxTagLength

            require(maxInputLength in 64..65_535) { "max_input_length must be in 64..65535" }
            require(maxNestingDepth in 1..64) { "max_nesting_depth must be in 1..64" }
            require(maxGradientChars in 1..8_192) { "max_gradient_chars must be in 1..8192" }
            require(maxTagLength in 8..512) { "max_tag_length must be in 8..512" }

            return RenderLimits(
                maxInputLength = maxInputLength,
                maxNestingDepth = maxNestingDepth,
                maxGradientChars = maxGradientChars,
                maxTagLength = maxTagLength,
            )
        }
    }

    private fun ensureDefaultFile(stylePath: Path) {
        stylePath.parent?.let { Files.createDirectories(it) }
        if (Files.exists(stylePath)) {
            return
        }
        Files.newBufferedWriter(stylePath, StandardCharsets.UTF_8).use { writer ->
            writer.write(
                """
                # ============================================================
                # Lineage Proxy - Text Rendering Limits
                # Tweak values only if you know what you're doing.
                # These limits protect against malformed/abusive markup.
                # ============================================================
                max_input_length = ${DEFAULT_LIMITS.maxInputLength}
                max_nesting_depth = ${DEFAULT_LIMITS.maxNestingDepth}
                max_gradient_chars = ${DEFAULT_LIMITS.maxGradientChars}
                max_tag_length = ${DEFAULT_LIMITS.maxTagLength}
                """.trimIndent()
            )
            writer.newLine()
        }
    }

    private val DEFAULT_LIMITS = RenderLimits()
}
