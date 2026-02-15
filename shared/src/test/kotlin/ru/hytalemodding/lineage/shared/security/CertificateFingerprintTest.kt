/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class CertificateFingerprintTest {
    @Test
    fun canonicalizesSha256Base64Url() {
        val raw = ByteArray(32)
        raw[0] = 1
        val encoded = Base64.getUrlEncoder().encodeToString(raw)
        val canonical = CertificateFingerprint.canonicalSha256Base64Url(encoded)
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(raw), canonical)
    }

    @Test
    fun rejectsInvalidFingerprintEncoding() {
        assertNull(CertificateFingerprint.canonicalSha256Base64Url("not-base64"))
        assertNull(CertificateFingerprint.canonicalSha256Base64Url("YQ")) // one-byte payload
    }
}
