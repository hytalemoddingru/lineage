/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.shared.security.CertificateFingerprint
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509ExtendedTrustManager

class PinnedCertificateTrustManagerFactoryTest {
    @Test
    fun acceptsMatchingFingerprint() {
        val cert = selfSignedCertificate()
        val fingerprint = CertificateFingerprint.sha256Base64Url(cert.encoded)
        val trustManager = createTrustManager(fingerprint)

        assertDoesNotThrow {
            trustManager.checkServerTrusted(arrayOf(cert), "EC")
        }
    }

    @Test
    fun rejectsMismatchedFingerprint() {
        val cert = selfSignedCertificate()
        val mismatch = selfSignedCertificate()
        val mismatchFingerprint = CertificateFingerprint.sha256Base64Url(mismatch.encoded)
        val trustManager = createTrustManager(mismatchFingerprint)

        assertThrows(CertificateException::class.java) {
            trustManager.checkServerTrusted(arrayOf(cert), "EC")
        }
    }

    private fun selfSignedCertificate(): X509Certificate {
        return ru.hytalemodding.lineage.proxy.util.CertificateUtil.generateSelfSigned().cert
    }

    private fun createTrustManager(expectedFingerprint: String): X509ExtendedTrustManager {
        val factory = PinnedCertificateTrustManagerFactory.forFingerprint(expectedFingerprint)
        factory.init(null as java.security.KeyStore?)
        return factory.trustManagers.single() as X509ExtendedTrustManager
    }
}
