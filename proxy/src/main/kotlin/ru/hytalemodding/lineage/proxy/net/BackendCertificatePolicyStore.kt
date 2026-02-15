/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import io.netty.incubator.codec.quic.QuicChannel
import ru.hytalemodding.lineage.proxy.config.BackendCertTrustMode
import ru.hytalemodding.lineage.proxy.config.BackendConfig
import ru.hytalemodding.lineage.proxy.util.Logging
import ru.hytalemodding.lineage.shared.security.CertificateFingerprint
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.TrustManagerFactory

/**
 * Runtime certificate policy for outbound proxy->backend QUIC links.
 */
class BackendCertificatePolicyStore(backends: Collection<BackendConfig>) {
    private val logger = Logging.logger(BackendCertificatePolicyStore::class.java)
    private val knownTofuPins = ConcurrentHashMap<String, String>()

    init {
        for (backend in backends) {
            if (backend.certTrustMode == BackendCertTrustMode.TOFU) {
                val initial = backend.certFingerprintSha256
                if (!initial.isNullOrBlank()) {
                    knownTofuPins[backend.id] = initial
                }
            }
        }
    }

    fun trustManagerFactoryFor(backend: BackendConfig): TrustManagerFactory {
        return when (backend.certTrustMode) {
            BackendCertTrustMode.STRICT_PINNED ->
                PinnedCertificateTrustManagerFactory.forFingerprint(requireConfiguredPin(backend))
            BackendCertTrustMode.TOFU ->
                AcceptAllServerCertificatesTrustManagerFactory
        }
    }

    /**
     * Validates the negotiated peer certificate according to backend policy and updates TOFU state.
     */
    fun verifyAndRecord(backend: BackendConfig, channel: QuicChannel): Boolean {
        val peerFingerprint = extractPeerFingerprint(channel) ?: return false
        return when (backend.certTrustMode) {
            BackendCertTrustMode.STRICT_PINNED -> {
                val expected = requireConfiguredPin(backend)
                peerFingerprint == expected
            }

            BackendCertTrustMode.TOFU -> {
                val previous = knownTofuPins.putIfAbsent(backend.id, peerFingerprint)
                when {
                    previous == null -> {
                        logger.debug("TOFU learned backend certificate pin for {}", backend.id)
                        true
                    }

                    previous == peerFingerprint -> true

                    else -> {
                        knownTofuPins[backend.id] = peerFingerprint
                        logger.warn("TOFU backend certificate rotated for {} (pin updated)", backend.id)
                        true
                    }
                }
            }
        }
    }

    internal fun currentTofuPin(backendId: String): String? = knownTofuPins[backendId]

    private fun requireConfiguredPin(backend: BackendConfig): String {
        return backend.certFingerprintSha256
            ?: error("Strict pinned mode requires certFingerprintSha256 for backend ${backend.id}")
    }

    private fun extractPeerFingerprint(channel: QuicChannel): String? {
        return try {
            val sslEngine = channel.sslEngine() ?: return null
            val certs = sslEngine.session.peerCertificates
            val cert = certs.firstOrNull { it is X509Certificate } as? X509Certificate ?: return null
            CertificateFingerprint.sha256Base64Url(cert.encoded)
        } catch (_: Exception) {
            null
        }
    }
}
