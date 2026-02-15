/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.i18n

import ru.hytalemodding.lineage.api.i18n.LocalizationService
import ru.hytalemodding.lineage.api.player.ProxyPlayer
import ru.hytalemodding.lineage.proxy.player.ProxyPlayerImpl

class ProxyLocalizationService(
    private val messages: ProxyMessages,
) : LocalizationService {
    override fun text(language: String?, key: String, vars: Map<String, String>): String {
        return messages.text(language, key, vars)
    }

    override fun render(player: ProxyPlayer, key: String, vars: Map<String, String>): String {
        return messages.text(resolveLanguage(player), key, vars)
    }

    override fun send(player: ProxyPlayer, key: String, vars: Map<String, String>) {
        player.sendMessage(render(player, key, vars))
    }

    private fun resolveLanguage(player: ProxyPlayer): String? {
        return (player as? ProxyPlayerImpl)?.language
    }
}
