/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.permission

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.permission.PermissionSubject

class PermissionCheckerImplTest {
    @Test
    fun matchesPermissionsCaseInsensitively() {
        val checker = PermissionCheckerImpl()
        val subject = NamedSubject("PlayerOne")
        val subjectLower = NamedSubject("playerone")

        checker.grant(subject, "lineage.command.mod")

        assertTrue(checker.hasPermission(subjectLower, "lineage.command.mod"))
        assertFalse(checker.hasPermission(subjectLower, "lineage.command.perm"))
    }

    private class NamedSubject(
        override val name: String,
    ) : PermissionSubject
}
