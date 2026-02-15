/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.i18n

import ru.hytalemodding.lineage.proxy.text.RenderLimits
import ru.hytalemodding.lineage.proxy.text.RenderStyleLoader
import ru.hytalemodding.lineage.proxy.util.Logging
import java.nio.file.Path

/**
 * Mutable runtime holder for localization bundles and text rendering limits.
 */
class LocalizationRuntime(
    private val messagesDir: Path,
    private val stylePath: Path,
) {
    private val logger = Logging.logger(LocalizationRuntime::class.java)
    val messages: ProxyMessages = ProxyMessagesLoader.load(messagesDir)

    @Volatile
    private var renderLimitsRef: RenderLimits = RenderStyleLoader.load(stylePath)

    fun renderLimits(): RenderLimits = renderLimitsRef

    fun reload(): ReloadResult {
        var messagesReloaded = false
        var styleReloaded = false
        val errors = mutableListOf<String>()

        runCatching { ProxyMessagesLoader.load(messagesDir) }
            .onSuccess {
                messages.replaceWith(it)
                messagesReloaded = true
            }
            .onFailure { errors.add("messages: ${it.message}") }

        runCatching { RenderStyleLoader.load(stylePath) }
            .onSuccess {
                renderLimitsRef = it
                styleReloaded = true
            }
            .onFailure { errors.add("style: ${it.message}") }

        if (errors.isNotEmpty()) {
            logger.warn("Localization reload completed with errors: {}", errors.joinToString(" | "))
        }

        return ReloadResult(
            messagesReloaded = messagesReloaded,
            styleReloaded = styleReloaded,
            errors = errors,
        )
    }
}

data class ReloadResult(
    val messagesReloaded: Boolean,
    val styleReloaded: Boolean,
    val errors: List<String>,
) {
    val success: Boolean
        get() = errors.isEmpty()
}
