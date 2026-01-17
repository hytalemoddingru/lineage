/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

/**
 * Version constraint for a dependency.
 */
data class VersionConstraint(
    val operator: Operator,
    val version: SemVer?,
) {
    enum class Operator {
        ANY,
        EQ,
        GT,
        GTE,
        LT,
        LTE,
        CARET,
    }

    fun matches(actual: SemVer): Boolean {
        return when (operator) {
            Operator.ANY -> true
            Operator.EQ -> actual == requireVersion()
            Operator.GT -> actual > requireVersion()
            Operator.GTE -> actual >= requireVersion()
            Operator.LT -> actual < requireVersion()
            Operator.LTE -> actual <= requireVersion()
            Operator.CARET -> {
                val base = requireVersion()
                actual.major == base.major && actual >= base
            }
        }
    }

    private fun requireVersion(): SemVer {
        return version ?: throw ModLoadException("Missing version for constraint $operator")
    }

    companion object {
        fun any(): VersionConstraint = VersionConstraint(Operator.ANY, null)
    }
}
