/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.agent;

import java.nio.charset.StandardCharsets;
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
            if (payloadParts.length > 5 && !payloadParts[5].isEmpty()) {
                return payloadParts[5];
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
