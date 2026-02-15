/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.logging

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Archives logs/latest.log on startup and keeps only the newest [maxArchives] archives.
 */
object ProxyLogArchiveService {
    private val archiveTimestampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun archiveAndPrune(logsDir: Path, maxArchives: Int = 10, clock: Clock = Clock.systemDefaultZone()) {
        require(maxArchives > 0) { "maxArchives must be > 0" }
        Files.createDirectories(logsDir)
        val archiveDir = logsDir.resolve("archive")
        Files.createDirectories(archiveDir)

        val latest = logsDir.resolve("latest.log")
        if (Files.exists(latest) && Files.size(latest) > 0L) {
            val baseName = archiveBaseName(clock)
            val archivedPath = uniqueArchivePath(archiveDir, baseName)
            Files.move(latest, archivedPath, StandardCopyOption.REPLACE_EXISTING)
        }

        pruneOldArchives(archiveDir, maxArchives)
    }

    private fun archiveBaseName(clock: Clock): String {
        val dateTime = LocalDateTime.ofInstant(clock.instant(), clock.zone)
        return "proxy-${archiveTimestampFormat.format(dateTime)}.log"
    }

    private fun uniqueArchivePath(archiveDir: Path, baseName: String): Path {
        var candidate = archiveDir.resolve(baseName)
        var suffix = 1
        while (Files.exists(candidate)) {
            val nameWithoutExt = baseName.removeSuffix(".log")
            candidate = archiveDir.resolve("$nameWithoutExt-$suffix.log")
            suffix++
        }
        return candidate
    }

    private fun pruneOldArchives(archiveDir: Path, maxArchives: Int) {
        val archives = Files.list(archiveDir).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) && it.name.endsWith(".log") }
                .map { path -> path to Files.getLastModifiedTime(path).toMillis() }
                .sortedByDescending { it.second }
                .toList()
        }
        if (archives.size <= maxArchives) {
            return
        }
        archives.drop(maxArchives).forEach { (path, _) ->
            Files.deleteIfExists(path)
        }
    }
}
