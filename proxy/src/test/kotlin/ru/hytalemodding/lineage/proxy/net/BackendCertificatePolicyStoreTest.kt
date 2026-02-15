/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.proxy.config.BackendCertTrustMode
import ru.hytalemodding.lineage.proxy.config.BackendConfig

class BackendCertificatePolicyStoreTest {
    @Test
    fun seedsConfiguredTofuPin() {
        val store = BackendCertificatePolicyStore(
            listOf(
                BackendConfig(
                    id = "hub",
                    host = "127.0.0.1",
                    port = 25580,
                    certFingerprintSha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    certTrustMode = BackendCertTrustMode.TOFU,
                )
            )
        )

        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", store.currentTofuPin("hub"))
    }

    @Test
    fun providesStrictTrustManagerWhenPinnedMode() {
        val store = BackendCertificatePolicyStore(
            listOf(
                BackendConfig(
                    id = "hub",
                    host = "127.0.0.1",
                    port = 25580,
                    certFingerprintSha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    certTrustMode = BackendCertTrustMode.STRICT_PINNED,
                )
            )
        )

        val factory = store.trustManagerFactoryFor(
            BackendConfig(
                id = "hub",
                host = "127.0.0.1",
                port = 25580,
                certFingerprintSha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                certTrustMode = BackendCertTrustMode.STRICT_PINNED,
            )
        )

        factory.init(null as java.security.KeyStore?)
        assertNotNull(factory.trustManagers.single())
    }
}
