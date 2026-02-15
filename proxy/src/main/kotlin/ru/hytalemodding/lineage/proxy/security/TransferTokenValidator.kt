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
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Validates transfer tokens used for cross-server routing.
 */
class TransferTokenValidator(
    private val secret: ByteArray,
    private val clock: Clock = SystemClock,
    private val replayMaxEntries: Int = DEFAULT_REPLAY_MAX_ENTRIES,
) {
    private val logger = Logging.logger(TransferTokenValidator::class.java)
    private val replayEntries = ConcurrentHashMap<String, ReplayEntry>()
    private val lastCleanupMillis = AtomicLong(0L)
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    init {
        require(replayMaxEntries > 0) { "replayMaxEntries must be > 0" }
    }

    fun isTransferTokenCandidate(encoded: String): Boolean {
        return encoded.startsWith(TRANSFER_TOKEN_PREFIX)
    }

    /**
     * Attempts to validate an encoded token for [expectedPlayerId].
     *
     * Returns null when the token is absent or invalid.
     */
    fun tryValidate(encoded: String, expectedPlayerId: String): TransferToken? {
        if (!isTransferTokenCandidate(encoded)) {
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
        val replayKey = encoder.encodeToString(parsed.signature)
        if (!tryRegisterReplay(replayKey, token.expiresAtMillis, now)) {
            logger.warn("Transfer token replay detected for player {}", expectedPlayerId)
            return null
        }
        return token
    }

    private fun tryRegisterReplay(key: String, expiresAtMillis: Long, now: Long): Boolean {
        cleanupIfNeeded(now)
        val existing = replayEntries[key]
        if (existing != null && existing.expiresAtMillis > now) {
            existing.lastSeenMillis = now
            return false
        }
        replayEntries[key] = ReplayEntry(expiresAtMillis = expiresAtMillis, lastSeenMillis = now)
        trimToSize()
        return true
    }

    private fun cleanupIfNeeded(now: Long) {
        val last = lastCleanupMillis.get()
        if (now - last < CLEANUP_INTERVAL_MILLIS) {
            return
        }
        if (!lastCleanupMillis.compareAndSet(last, now)) {
            return
        }
        replayEntries.entries.removeIf { it.value.expiresAtMillis <= now }
    }

    private fun trimToSize() {
        if (replayEntries.size <= replayMaxEntries) {
            return
        }
        val overflow = replayEntries.size - replayMaxEntries
        val victims = replayEntries.entries
            .sortedBy { it.value.lastSeenMillis }
            .take(overflow)
        for (entry in victims) {
            replayEntries.remove(entry.key)
        }
    }

    private data class ReplayEntry(
        val expiresAtMillis: Long,
        var lastSeenMillis: Long,
    )

    private companion object {
        private const val TRANSFER_TOKEN_PREFIX = "t1."
        private const val DEFAULT_REPLAY_MAX_ENTRIES = 100_000
        private const val CLEANUP_INTERVAL_MILLIS = 1_000L
    }
}
