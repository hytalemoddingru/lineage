/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.text

import ru.hytalemodding.lineage.api.player.ProxyPlayer
import ru.hytalemodding.lineage.api.text.TextRendererService

class ProxyTextRendererService(
    private val renderLimitsProvider: () -> RenderLimits,
) : TextRendererService {
    override fun renderForPlayer(player: ProxyPlayer, rawMarkup: String): String {
        return MiniTextRenderer.render(rawMarkup, RenderProfile.GAME, renderLimitsProvider())
    }

    override fun renderForConsole(rawMarkup: String): String {
        return MiniTextRenderer.render(rawMarkup, RenderProfile.CONSOLE, renderLimitsProvider())
    }

    override fun renderPlain(rawMarkup: String): String {
        return MiniTextRenderer.render(rawMarkup, RenderProfile.PLAIN, renderLimitsProvider())
    }
}
