/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ProxyMessagesLoaderTest {
    @Test
    fun loadMergesWithDefaultsWhenCustomFileDefinesPartialLanguage() {
        val dir = Files.createTempDirectory("lineage-messages")
        val custom = dir.resolve("en-us.toml")
        Files.writeString(
            custom,
            """
                schema_version = 1
                language = "en-us"
                transfer_failed = "&cCustom transfer failed"
            """.trimIndent()
        )

        val messages = ProxyMessagesLoader.load(dir)

        assertEquals("&cCustom transfer failed", messages.text("en-US", "transfer_failed"))
        assertTrue(messages.text("en-US", "transfer_usage", mapOf("usage" to "x")).contains("x"))
    }

    @Test
    fun loadCreatesDefaultLanguageFiles() {
        val dir = Files.createTempDirectory("lineage-messages-defaults")

        ProxyMessagesLoader.load(dir)

        assertTrue(Files.exists(dir.resolve("en-us.toml")))
        assertTrue(Files.exists(dir.resolve("ru-ru.toml")))
    }

    @Test
    fun normalizeLanguageSupportsConfiguredCustomLanguages() {
        val custom = ProxyMessages(
            defaultLanguage = "en-us",
            bundles = mapOf(
                "en-us" to mapOf("k" to "v"),
                "de-de" to mapOf("k" to "v2"),
            ),
        )

        assertEquals("de-de", custom.normalizeLanguage("de_DE"))
        assertEquals("en-us", custom.normalizeLanguage("unknown"))
    }
}
