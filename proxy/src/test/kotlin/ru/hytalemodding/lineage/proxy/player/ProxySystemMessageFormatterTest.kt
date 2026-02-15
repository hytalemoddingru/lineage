/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader

class ProxySystemMessageFormatterTest {
    @Test
    fun formatsSystemMessageWithRuLocalization() {
        val formatted = ProxySystemMessageFormatter.format("ru-RU", "test", ProxyMessagesLoader.defaults())

        assertTrue(formatted.contains("\u00A7x"))
        assertTrue(formatted.contains("|"))
        assertTrue(formatted.endsWith("test"))
    }

    @Test
    fun fallsBackToEnglishForUnknownLanguage() {
        val formatted = ProxySystemMessageFormatter.format("de-DE", "hello", ProxyMessagesLoader.defaults())

        assertTrue(formatted.contains("\u00A7x"))
        assertTrue(formatted.contains("|"))
        assertTrue(formatted.endsWith("hello"))
    }

    @Test
    fun normalizesLanguageCodes() {
        val messages = ProxyMessagesLoader.defaults()
        assertEquals("ru-ru", ProxySystemMessageFormatter.normalizeLanguage("ru", messages))
        assertEquals("en-us", ProxySystemMessageFormatter.normalizeLanguage("en_US", messages))
        assertEquals("en-us", ProxySystemMessageFormatter.normalizeLanguage("unknown", messages))
        assertEquals("en-us", ProxySystemMessageFormatter.normalizeLanguage(null, messages))
    }

    @Test
    fun convertsHexColorMarkupToSectionHex() {
        val formatted = ProxySystemMessageFormatter.format("en-US", " <#33CCFF>hex", ProxyMessagesLoader.defaults())
        assertTrue(formatted.contains("\u00A7x\u00A73\u00A73\u00A7c\u00A7c\u00A7f\u00A7f"))
    }
}
