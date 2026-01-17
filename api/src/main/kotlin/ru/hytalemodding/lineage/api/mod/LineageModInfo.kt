/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.mod

/**
 * Declares metadata for a Lineage mod.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LineageModInfo(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: String,
    val authors: Array<String> = [],
    val description: String = "",
    val dependencies: Array<String> = [],
    val softDependencies: Array<String> = [],
    val website: String = "",
    val license: String = "",
)
