/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Logger factory helper.
 */
object Logging {
    /**
     * Returns a logger for the provided [type].
     */
    fun logger(type: Class<*>): Logger = LoggerFactory.getLogger(type)
}
