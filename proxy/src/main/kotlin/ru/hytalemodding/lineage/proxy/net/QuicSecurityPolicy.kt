/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

/**
 * Security-critical QUIC transport policy shared across inbound/outbound paths.
 */
object QuicSecurityPolicy {
    const val PRIMARY_ALPN = "hytale/2"
    const val LEGACY_ALPN = "hytale/1"

    private val acceptedNegotiatedAlpn = setOf(PRIMARY_ALPN)

    fun advertisedAlpnProtocols(): Array<String> = arrayOf(PRIMARY_ALPN, LEGACY_ALPN)

    fun isAcceptedNegotiatedAlpn(negotiated: String?): Boolean {
        val normalized = normalizeNegotiatedAlpn(negotiated)
        return normalized in acceptedNegotiatedAlpn
    }

    fun normalizeNegotiatedAlpn(negotiated: String?): String? {
        val value = negotiated?.trim().orEmpty()
        return value.takeIf { it.isNotEmpty() }
    }
}
