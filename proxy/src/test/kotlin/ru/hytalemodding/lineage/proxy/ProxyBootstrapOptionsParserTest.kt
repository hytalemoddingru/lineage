/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ProxyBootstrapOptionsParserTest {
    @Test
    fun parsesDefaultOptions() {
        val options = ProxyBootstrapOptionsParser.parse(emptyArray())

        assertEquals(Path.of("config.toml"), options.configPath)
        assertEquals(false, options.strictMode)
    }

    @Test
    fun parsesStrictModeAndConfigPath() {
        val options = ProxyBootstrapOptionsParser.parse(arrayOf("--strict", "custom.toml"))

        assertEquals(Path.of("custom.toml"), options.configPath)
        assertEquals(true, options.strictMode)
    }

    @Test
    fun rejectsUnknownFlag() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyBootstrapOptionsParser.parse(arrayOf("--unknown"))
        }
    }

    @Test
    fun rejectsMultipleConfigPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyBootstrapOptionsParser.parse(arrayOf("a.toml", "b.toml"))
        }
    }
}

