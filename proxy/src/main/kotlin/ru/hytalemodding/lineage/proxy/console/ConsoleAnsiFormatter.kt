/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.console

import ru.hytalemodding.lineage.proxy.text.MiniTextRenderer
import ru.hytalemodding.lineage.proxy.text.RenderLimits
import ru.hytalemodding.lineage.proxy.text.RenderProfile

/**
 * Converts section/ampersand color codes and hex color markup to ANSI escape sequences.
 */
object ConsoleAnsiFormatter {
    fun render(message: String, renderLimits: RenderLimits = RenderLimits()): String {
        return MiniTextRenderer.render(message, RenderProfile.CONSOLE, renderLimits)
    }
}
