/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy

import java.util.Properties

object ProxyRuntimeInfo {
    val version: String by lazy { loadVersion() }

    private fun loadVersion(): String {
        val resource = "/lineage-version.properties"
        val stream = ProxyRuntimeInfo::class.java.getResourceAsStream(resource)
            ?: return "dev"
        return stream.use {
            val properties = Properties()
            properties.load(it)
            properties.getProperty("version")?.trim().orEmpty().ifEmpty { "dev" }
        }
    }
}
