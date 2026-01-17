/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import ru.hytalemodding.lineage.api.mod.ModInfo

/**
 * Validates mod metadata fields for loader safety.
 */
object ModMetadataValidator {
    private val idRegex = Regex("^[a-z0-9_-]{1,32}$")
    private val nameRegex = Regex("^[A-Za-z0-9][A-Za-z0-9 _.-]{0,63}$")
    private val semverRegex = Regex("^\\d+\\.\\d+\\.\\d+$")

    fun validate(info: ModInfo) {
        val errors = mutableListOf<String>()
        if (!idRegex.matches(info.id)) {
            errors.add("id must match ${idRegex.pattern}")
        }
        if (!nameRegex.matches(info.name)) {
            errors.add("name must match ${nameRegex.pattern}")
        }
        if (!semverRegex.matches(info.version)) {
            errors.add("version must be MAJOR.MINOR.PATCH")
        }
        if (!semverRegex.matches(info.apiVersion)) {
            errors.add("apiVersion must be MAJOR.MINOR.PATCH")
        }
        if (info.authors.any { it.isBlank() }) {
            errors.add("authors must not be blank")
        }
        if (info.dependencies.any { it.isBlank() }) {
            errors.add("dependencies must not be blank")
        }
        if (info.softDependencies.any { it.isBlank() }) {
            errors.add("softDependencies must not be blank")
        }

        if (errors.isNotEmpty()) {
            throw ModLoadException("Invalid mod metadata for ${info.id}: ${errors.joinToString("; ")}")
        }
    }
}
