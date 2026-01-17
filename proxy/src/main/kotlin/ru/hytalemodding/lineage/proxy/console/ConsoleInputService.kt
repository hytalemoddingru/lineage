/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.console

import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.proxy.command.CommandDispatcher
import ru.hytalemodding.lineage.proxy.util.Logging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads console input and dispatches commands.
 */
class ConsoleInputService(
    private val dispatcher: CommandDispatcher,
    private val sender: CommandSender,
) : AutoCloseable {
    private val logger = Logging.logger(ConsoleInputService::class.java)
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        val reader = BufferedReader(InputStreamReader(System.`in`))
        val worker = Thread {
            while (running.get()) {
                val line = try {
                    reader.readLine()
                } catch (ex: Exception) {
                    logger.warn("Console input failed", ex)
                    break
                } ?: break
                if (line.isBlank()) {
                    continue
                }
                dispatcher.dispatch(sender, line)
            }
        }
        worker.isDaemon = true
        worker.name = "lineage-console"
        worker.start()
        thread = worker
    }

    override fun close() {
        running.set(false)
        thread?.interrupt()
    }
}
