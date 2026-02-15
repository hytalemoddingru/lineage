/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthModePolicyTest {
    @Test
    fun rejectsWhenAuthenticatedModeIsRequiredButNotActive() {
        assertTrue(AuthModePolicy.shouldRejectHandshake(requireAuthenticatedMode = true, authModeAuthenticated = false))
    }

    @Test
    fun allowsWhenAuthenticatedModeIsRequiredAndActive() {
        assertFalse(AuthModePolicy.shouldRejectHandshake(requireAuthenticatedMode = true, authModeAuthenticated = true))
    }

    @Test
    fun allowsWhenRequirementIsDisabled() {
        assertFalse(AuthModePolicy.shouldRejectHandshake(requireAuthenticatedMode = false, authModeAuthenticated = false))
    }

    @Test
    fun exposesDeterministicRejectMessage() {
        assertEquals("Server requires AUTHENTICATED mode.", AuthModePolicy.REQUIRED_AUTH_MESSAGE)
    }
}

