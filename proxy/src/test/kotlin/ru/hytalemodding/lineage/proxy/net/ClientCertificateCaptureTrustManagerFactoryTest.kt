/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClientCertificateCaptureTrustManagerFactoryTest {
    @Test
    fun providesAtLeastOneTrustManager() {
        ClientCertificateCaptureTrustManagerFactory.init(null as java.security.KeyStore?)
        val managers = ClientCertificateCaptureTrustManagerFactory.trustManagers
        assertNotNull(managers)
        assertTrue(managers.isNotEmpty())
    }
}
