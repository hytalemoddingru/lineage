/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.i18n

import ru.hytalemodding.lineage.api.player.ProxyPlayer
import ru.hytalemodding.lineage.api.service.ServiceKey

/**
 * Localization facade for mods.
 */
interface LocalizationService {
    fun text(language: String?, key: String, vars: Map<String, String> = emptyMap()): String

    fun render(player: ProxyPlayer, key: String, vars: Map<String, String> = emptyMap()): String

    fun send(player: ProxyPlayer, key: String, vars: Map<String, String> = emptyMap())

    companion object {
        val SERVICE_KEY: ServiceKey<LocalizationService> = ServiceKey(LocalizationService::class.java)
    }
}
