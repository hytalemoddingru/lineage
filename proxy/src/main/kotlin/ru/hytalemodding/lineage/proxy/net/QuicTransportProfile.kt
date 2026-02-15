/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import java.util.concurrent.TimeUnit

/**
 * QUIC baseline profile aligned with current kernel networking limits.
 */
object QuicTransportProfile {
    const val IDLE_TIMEOUT_VALUE = 120L
    val IDLE_TIMEOUT_UNIT: TimeUnit = TimeUnit.SECONDS

    const val INITIAL_MAX_DATA = 524_288L
    const val INITIAL_MAX_STREAM_DATA_BIDI = 131_072L
    const val INITIAL_MAX_STREAMS_BIDIRECTIONAL = 1L
    const val BACKEND_CONNECT_TIMEOUT_MILLIS = 5_000L
}
