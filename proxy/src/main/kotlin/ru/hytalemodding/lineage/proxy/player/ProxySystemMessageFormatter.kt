/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.player

import ru.hytalemodding.lineage.proxy.i18n.ProxyMessages
import ru.hytalemodding.lineage.proxy.i18n.ProxyMessagesLoader
import ru.hytalemodding.lineage.proxy.text.MiniTextRenderer
import ru.hytalemodding.lineage.proxy.text.RenderLimits
import ru.hytalemodding.lineage.proxy.text.RenderProfile

object ProxySystemMessageFormatter {
    fun format(
        language: String?,
        message: String,
        messages: ProxyMessages = ProxyMessagesLoader.defaults(),
        renderLimits: RenderLimits = RenderLimits(),
    ): String {
        val prefix = messages.text(language, "proxy_prefix")
        val formatted = withPrefix(prefix, message)
        return MiniTextRenderer.render(formatted, RenderProfile.GAME, renderLimits)
    }

    fun normalizeLanguage(
        language: String?,
        messages: ProxyMessages = ProxyMessagesLoader.defaults(),
    ): String {
        return messages.normalizeLanguage(language)
    }

    private fun withPrefix(prefix: String, message: String): String {
        if (message.isEmpty()) {
            return prefix
        }
        val newlineIndex = message.indexOf('\n')
        if (newlineIndex < 0) {
            return prefix + message
        }
        return buildString(prefix.length + message.length) {
            append(prefix)
            append(message, 0, newlineIndex + 1)
            append(message, newlineIndex + 1, message.length)
        }
    }
}
