/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.time

/**
 * Time source for token logic and validation.
 * Helps keep time-dependent logic testable.
 */
interface Clock {
    /**
     * Returns current time in milliseconds since Unix Epoch.
     */
    fun nowMillis(): Long
}

/**
 * System clock implementation based on [System.currentTimeMillis].
 */
object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
