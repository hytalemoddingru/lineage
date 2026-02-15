/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.i18n

/**
 * In-memory localized proxy messages.
 */
class ProxyMessages(
    defaultLanguage: String,
    bundles: Map<String, Map<String, String>>,
) {
    @Volatile
    private var defaultLanguage: String = defaultLanguage
    @Volatile
    private var bundles: Map<String, Map<String, String>> = bundles

    fun text(language: String?, key: String, vars: Map<String, String> = emptyMap()): String {
        val snapshotDefaultLanguage = defaultLanguage
        val snapshotBundles = bundles
        val normalized = normalizeLanguage(language)
        val template = snapshotBundles[normalized]?.get(key)
            ?: snapshotBundles[snapshotDefaultLanguage]?.get(key)
            ?: snapshotBundles[FALLBACK_LANGUAGE]?.get(key)
            ?: key
        if (vars.isEmpty()) {
            return template
        }
        var rendered = template
        for ((name, value) in vars) {
            rendered = rendered.replace("{$name}", value)
        }
        return rendered
    }

    fun normalizeLanguage(language: String?): String {
        val snapshotDefaultLanguage = defaultLanguage
        val snapshotBundles = bundles
        val normalized = language?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) {
            return snapshotDefaultLanguage
        }
        val canonical = normalized.replace('_', '-')
        if (canonical in snapshotBundles) {
            return canonical
        }
        return when (canonical) {
            "ru" -> if ("ru-ru" in snapshotBundles) "ru-ru" else snapshotDefaultLanguage
            "en" -> if ("en-us" in snapshotBundles) "en-us" else snapshotDefaultLanguage
            else -> snapshotDefaultLanguage
        }
    }

    fun replaceWith(other: ProxyMessages) {
        val state = other.snapshot()
        defaultLanguage = state.defaultLanguage
        bundles = state.bundles
    }

    internal fun snapshot(): Snapshot {
        return Snapshot(
            defaultLanguage = defaultLanguage,
            bundles = bundles,
        )
    }

    internal data class Snapshot(
        val defaultLanguage: String,
        val bundles: Map<String, Map<String, String>>,
    )

    companion object {
        const val FALLBACK_LANGUAGE: String = "en-us"
    }
}
