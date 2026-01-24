/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.shared.control

data class ControlEnvelope(
    val version: Byte,
    val type: ControlMessageType,
    val senderId: String,
    val issuedAtMillis: Long,
    val ttlMillis: Long,
    val nonce: ByteArray,
    val payload: ByteArray,
)
