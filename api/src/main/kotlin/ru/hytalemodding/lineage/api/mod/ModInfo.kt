/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.mod

/**
 * Resolved mod metadata used at runtime.
 */
data class ModInfo(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: String,
    val authors: List<String>,
    val description: String,
    val dependencies: List<String>,
    val softDependencies: List<String>,
    val capabilities: Set<ModCapability>,
    val website: String?,
    val license: String?,
)
