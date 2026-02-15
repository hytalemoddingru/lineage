/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.console

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConsoleAnsiFormatterTest {
    @Test
    fun rendersInlineHexColor() {
        val rendered = ConsoleAnsiFormatter.render("<#33CCFF>Hello")
        assertTrue(rendered.contains("\u001B[38;2;51;204;255m"))
    }

    @Test
    fun rendersSectionHexColor() {
        val rendered = ConsoleAnsiFormatter.render("\u00A7x\u00A73\u00A73\u00A7c\u00A7c\u00A7f\u00A7fHello")
        assertTrue(rendered.contains("\u001B[38;2;51;204;255m"))
    }
}
