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

class MiniTextRendererTest {
    @Test
    fun rendersNamedColorTagForGameProfile() {
        val rendered = MiniTextRenderer.render("<red>Hello</red>", RenderProfile.GAME)
        assertTrue(rendered.contains("\u00A7c"))
        assertTrue(rendered.endsWith("Hello\u00A7r"))
    }

    @Test
    fun rendersGradientForGameProfile() {
        val rendered = MiniTextRenderer.render(
            "<gradient:#ff0000:#00ff00>AB</gradient>",
            RenderProfile.GAME,
        )
        assertTrue(rendered.contains("\u00A7x\u00A7f\u00A7f\u00A70\u00A70\u00A70\u00A70A"))
        assertTrue(rendered.contains("\u00A7x\u00A70\u00A70\u00A7f\u00A7f\u00A70\u00A70B"))
    }

    @Test
    fun stripsFormattingInPlainProfile() {
        val rendered = MiniTextRenderer.render(
            "<bold><red>Hello</red></bold> <#33ccff>world",
            RenderProfile.PLAIN,
        )
        assertEquals("Hello world", rendered)
    }

    @Test
    fun respectsInputLengthLimit() {
        val rendered = MiniTextRenderer.render(
            "0123456789abcdef",
            RenderProfile.PLAIN,
            RenderLimits(maxInputLength = 8),
        )
        assertEquals("01234567", rendered)
    }

    @Test
    fun rejectsDeepNestingBeyondLimitDeterministically() {
        val rendered = MiniTextRenderer.render(
            "<bold><italic>x</italic></bold>",
            RenderProfile.PLAIN,
            RenderLimits(maxNestingDepth = 1),
        )
        assertEquals("<italic>x</italic>", rendered)
    }
}
