/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.name
import kotlin.streams.asSequence

class ProxyLogArchiveServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun archivesLatestLogAndKeepsNewestTenArchives() {
        val logsDir = tempDir.resolve("logs")
        val archiveDir = logsDir.resolve("archive")
        Files.createDirectories(archiveDir)
        val latest = logsDir.resolve("latest.log")
        Files.createDirectories(logsDir)
        Files.writeString(latest, "newest content")

        repeat(12) { index ->
            val file = archiveDir.resolve("proxy-old-$index.log")
            Files.writeString(file, "old-$index")
            val modified = Instant.parse("2026-02-14T10:00:00Z").minusSeconds((index + 1).toLong())
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(modified))
        }

        val fixedClock = Clock.fixed(Instant.parse("2026-02-14T12:34:56Z"), ZoneId.of("UTC"))
        ProxyLogArchiveService.archiveAndPrune(logsDir, maxArchives = 10, clock = fixedClock)

        assertFalse(Files.exists(latest))
        val archives = Files.list(archiveDir).use { stream ->
            stream.asSequence().filter { Files.isRegularFile(it) }.toList()
        }
        assertEquals(10, archives.size)
        assertTrue(archives.any { it.name.startsWith("proxy-20260214-123456") })
        assertFalse(archives.any { it.name == "proxy-old-10.log" })
        assertFalse(archives.any { it.name == "proxy-old-11.log" })
    }
}
