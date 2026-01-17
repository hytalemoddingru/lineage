/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.security

import ru.hytalemodding.lineage.proxy.util.Logging
import ru.hytalemodding.lineage.shared.time.Clock
import ru.hytalemodding.lineage.shared.time.SystemClock
import ru.hytalemodding.lineage.shared.token.CURRENT_TRANSFER_TOKEN_VERSION
import ru.hytalemodding.lineage.shared.token.TransferToken
import ru.hytalemodding.lineage.shared.token.TransferTokenCodec
import ru.hytalemodding.lineage.shared.token.TransferTokenFormatException

/**
 * Validates transfer tokens used for cross-server routing.
 */
class TransferTokenValidator(
    private val secret: ByteArray,
    private val clock: Clock = SystemClock,
) {
    private val logger = Logging.logger(TransferTokenValidator::class.java)

    /**
     * Attempts to validate an encoded token for [expectedPlayerId].
     *
     * Returns null when the token is absent or invalid.
     */
    fun tryValidate(encoded: String, expectedPlayerId: String): TransferToken? {
        if (!encoded.startsWith("t1.")) {
            return null
        }
        val parsed = try {
            TransferTokenCodec.decode(encoded)
        } catch (ex: TransferTokenFormatException) {
            logger.debug("Invalid transfer token format: {}", ex.message)
            return null
        } catch (ex: IllegalArgumentException) {
            logger.debug("Invalid transfer token: {}", ex.message)
            return null
        }
        if (!TransferTokenCodec.verifySignature(parsed, secret)) {
            logger.debug("Transfer token signature invalid for player {}", expectedPlayerId)
            return null
        }
        val token = parsed.token
        if (token.version != CURRENT_TRANSFER_TOKEN_VERSION) {
            logger.debug("Unsupported transfer token version {}", token.version)
            return null
        }
        if (token.playerId != expectedPlayerId) {
            logger.debug("Transfer token player mismatch: expected {}, got {}", expectedPlayerId, token.playerId)
            return null
        }
        val now = clock.nowMillis()
        if (token.issuedAtMillis > now || token.expiresAtMillis < now) {
            logger.debug("Transfer token expired or not yet valid for player {}", expectedPlayerId)
            return null
        }
        return token
    }
}
