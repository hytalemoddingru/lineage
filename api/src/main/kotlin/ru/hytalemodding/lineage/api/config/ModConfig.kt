/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.config

import java.nio.file.Path
import java.time.Duration

/**
 * Represents a single mod configuration file.
 */
interface ModConfig {
    val name: String
    val path: Path

    fun reload(): ModConfig
    fun save()

    fun contains(path: String): Boolean
    fun remove(path: String)
    fun set(path: String, value: Any?)

    fun getString(path: String): String?
    fun getString(path: String, defaultValue: String): String

    fun getInt(path: String): Int?
    fun getInt(path: String, defaultValue: Int): Int

    fun getLong(path: String): Long?
    fun getLong(path: String, defaultValue: Long): Long

    fun getBoolean(path: String): Boolean?
    fun getBoolean(path: String, defaultValue: Boolean): Boolean

    fun getDuration(path: String): Duration?
    fun getDuration(path: String, defaultValue: Duration): Duration

    fun getStringList(path: String): List<String>
}
