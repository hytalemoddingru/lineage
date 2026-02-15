/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.security

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SecretStrengthPolicyTest {
    @Test
    fun rejectsDefaultSecretEquivalent() {
        val message = SecretStrengthPolicy.validationError("change me please", "security.proxy_secret")
        assertNotNull(message)
    }

    @Test
    fun acceptsNonDefaultSecret() {
        val message = SecretStrengthPolicy.validationError("secret-123-strong", "security.proxy_secret")
        assertNull(message)
    }
}
