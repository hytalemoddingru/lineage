/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.security

import java.security.MessageDigest
import java.util.Base64

/**
 * Utilities for SHA-256 certificate fingerprint handling in base64url format.
 */
object CertificateFingerprint {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun sha256Base64Url(certDer: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certDer)
        return encoder.encodeToString(digest)
    }

    fun canonicalSha256Base64Url(value: String): String? {
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            return null
        }
        val bytes = runCatching { decoder.decode(normalized) }.getOrNull() ?: return null
        if (bytes.size != 32) {
            return null
        }
        return encoder.encodeToString(bytes)
    }
}
