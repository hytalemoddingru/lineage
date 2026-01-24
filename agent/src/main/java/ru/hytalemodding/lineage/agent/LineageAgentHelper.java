/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.agent;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Minimal proxy token parser used by the agent during handshake.
 */
public final class LineageAgentHelper {
    private LineageAgentHelper() {
    }

    /**
     * Extracts the proxy certificate fingerprint from referral token payload.
     */
    public static String extractProxyFingerprint(byte[] referralData) {
        if (referralData == null || referralData.length == 0) {
            return null;
        }
        try {
            String token = new String(referralData, StandardCharsets.UTF_8).trim();
            if (token.isEmpty()) {
                return null;
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\|", -1);
            if (payloadParts.length < 5) {
                return null;
            }
            int version;
            try {
                version = Integer.parseInt(payloadParts[0]);
            } catch (NumberFormatException ex) {
                return null;
            }
            String certB64;
            if (version >= 3) {
                certB64 = payloadParts.length > 7 ? payloadParts[7] : null;
            } else if (version >= 2) {
                certB64 = payloadParts.length > 6 ? payloadParts[6] : null;
            } else {
                certB64 = payloadParts.length > 5 ? payloadParts[5] : null;
            }
            if (certB64 == null || certB64.isEmpty()) {
                return null;
            }
            String fingerprint = tryParseCertificateFingerprint(certB64);
            return fingerprint != null ? fingerprint : certB64;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String tryParseCertificateFingerprint(String certB64) {
        try {
            byte[] certBytes = Base64.getUrlDecoder().decode(certB64);
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(cert.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ignored) {
            return null;
        }
    }
}
