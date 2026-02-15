/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.text

import ru.hytalemodding.lineage.api.player.ProxyPlayer
import ru.hytalemodding.lineage.api.service.ServiceKey

/**
 * Markup renderer facade for mods.
 */
interface TextRendererService {
    fun renderForPlayer(player: ProxyPlayer, rawMarkup: String): String

    fun renderForConsole(rawMarkup: String): String

    fun renderPlain(rawMarkup: String): String

    companion object {
        val SERVICE_KEY: ServiceKey<TextRendererService> = ServiceKey(TextRendererService::class.java)
    }
}
