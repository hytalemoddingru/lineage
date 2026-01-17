/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

/**
 * Dependency specification parsed from a metadata entry.
 */
data class DependencySpec(
    val id: String,
    val constraint: VersionConstraint,
) {
    companion object {
        private val idRegex = Regex("^[a-z0-9_-]{1,32}$")
        private val operators = listOf(">=", "<=", ">", "<", "=", "^")

        fun parse(raw: String): DependencySpec {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                throw ModLoadException("Dependency entry must not be blank")
            }
            val operator = operators.firstOrNull { trimmed.contains(it) }
            if (operator == null) {
                validateId(trimmed)
                return DependencySpec(trimmed, VersionConstraint.any())
            }
            val parts = trimmed.split(operator, limit = 2)
            val id = parts[0].trim()
            val versionText = parts.getOrNull(1)?.trim().orEmpty()
            validateId(id)
            if (versionText.isEmpty()) {
                throw ModLoadException("Dependency $id is missing version after '$operator'")
            }
            val version = SemVer.parse(versionText)
                ?: throw ModLoadException("Dependency $id has invalid version: $versionText")
            val constraint = VersionConstraint(
                operator = when (operator) {
                    ">=" -> VersionConstraint.Operator.GTE
                    "<=" -> VersionConstraint.Operator.LTE
                    ">" -> VersionConstraint.Operator.GT
                    "<" -> VersionConstraint.Operator.LT
                    "=" -> VersionConstraint.Operator.EQ
                    "^" -> VersionConstraint.Operator.CARET
                    else -> VersionConstraint.Operator.ANY
                },
                version = version,
            )
            return DependencySpec(id, constraint)
        }

        private fun validateId(id: String) {
            if (!idRegex.matches(id)) {
                throw ModLoadException("Dependency id must match ${idRegex.pattern}: $id")
            }
        }
    }
}
