/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.mod.ModCapability
import ru.hytalemodding.lineage.api.mod.ModInfo
import ru.hytalemodding.lineage.api.service.ServiceKey
import ru.hytalemodding.lineage.proxy.security.TokenService
import ru.hytalemodding.lineage.proxy.service.ServiceRegistryImpl

class GuardedServiceRegistryTest {
    @Test
    fun allowsPublicServiceKeysWhenCapabilityGranted() {
        val registry = ServiceRegistryImpl()
        val guarded = GuardedServiceRegistry(registry, ModCapabilityGuard(modInfo(setOf(ModCapability.SERVICES))))
        val key = ServiceKey(String::class.java, "public")

        guarded.register(key, "value")

        assertEquals("value", guarded.get(key))
    }

    @Test
    fun rejectsSecurityServiceType() {
        val registry = ServiceRegistryImpl()
        val guarded = GuardedServiceRegistry(registry, ModCapabilityGuard(modInfo(setOf(ModCapability.SERVICES))))
        val key = ServiceKey(TokenService::class.java)
        val tokenService = TokenService("secret-123".toByteArray(), tokenTtlMillis = 30_000)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            guarded.register(key, tokenService)
        }
        assertNotNull(ex.message)
    }

    @Test
    fun rejectsReservedServiceKeyName() {
        val registry = ServiceRegistryImpl()
        val guarded = GuardedServiceRegistry(registry, ModCapabilityGuard(modInfo(setOf(ModCapability.SERVICES))))
        val key = ServiceKey(String::class.java, "lineage.internal.security")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            guarded.register(key, "value")
        }
        assertNotNull(ex.message)
    }

    @Test
    fun rejectsRegistryAccessWithoutServicesCapability() {
        val registry = ServiceRegistryImpl()
        val guarded = GuardedServiceRegistry(registry, ModCapabilityGuard(modInfo(emptySet())))
        val key = ServiceKey(String::class.java, "public")

        assertThrows(IllegalStateException::class.java) {
            guarded.register(key, "value")
        }
    }

    private fun modInfo(capabilities: Set<ModCapability>): ModInfo {
        return ModInfo(
            id = "test-mod",
            name = "Test Mod",
            version = "1.0.0",
            apiVersion = "0.4.0",
            authors = listOf("tester"),
            description = "",
            dependencies = emptyList(),
            softDependencies = emptyList(),
            capabilities = capabilities,
            website = null,
            license = null,
        )
    }
}
