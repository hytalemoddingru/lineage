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
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.ParsedLine
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads console input and dispatches commands.
 */
class ConsoleInputService(
    private val dispatcher: CommandDispatcher,
    private val sender: CommandSender,
    private val historyPath: Path,
    private val prompt: String,
    private val historyLimit: Int = 50,
    private val interruptCommand: String = "stop",
) : AutoCloseable {
    private val logger = Logging.logger(ConsoleInputService::class.java)
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var terminal: Terminal? = null

    init {
        require(historyLimit > 0) { "historyLimit must be > 0" }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        val worker = Thread {
            val terminal = try {
                TerminalBuilder.builder()
                    .system(true)
                    .build()
            } catch (ex: Exception) {
                logger.warn("Console terminal init failed", ex)
                running.set(false)
                return@Thread
            }
            this.terminal = terminal
            val reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(DispatcherCompleter(dispatcher, sender))
                .build()
            reader.option(LineReader.Option.AUTO_FRESH_LINE, true)
            reader.setVariable(LineReader.HISTORY_FILE, historyPath)
            reader.setVariable(LineReader.HISTORY_SIZE, historyLimit)
            reader.setVariable(LineReader.HISTORY_FILE_SIZE, historyLimit)
            ACTIVE_READER = reader

            while (running.get()) {
                val line = try {
                    reader.readLine(prompt)
                } catch (_: UserInterruptException) {
                    if (!running.get()) {
                        break
                    }
                    dispatcher.dispatch(sender, interruptCommand)
                    break
                } catch (_: EndOfFileException) {
                    break
                } catch (error: Throwable) {
                    if (!running.get()) {
                        break
                    }
                    logger.warn("Console input failed, requesting graceful shutdown", error)
                    runCatching { dispatcher.dispatch(sender, interruptCommand) }
                    break
                }
                if (line.isBlank()) {
                    continue
                }
                dispatcher.dispatch(sender, line)
            }
            runCatching { reader.history.save() }
            if (ACTIVE_READER === reader) {
                ACTIVE_READER = null
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
        ACTIVE_READER = null
        runCatching { terminal?.close() }
    }

    private class DispatcherCompleter(
        private val dispatcher: CommandDispatcher,
        private val sender: CommandSender,
    ) : Completer {
        override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
            val input = line.line()
            val suggestions = dispatcher.complete(sender, input)
            suggestions.forEach { suggestion ->
                candidates.add(Candidate(suggestion))
            }
        }
    }

    companion object {
        private val OUTPUT_LOCK = Any()
        @Volatile
        private var ACTIVE_READER: LineReader? = null

        fun printOutput(message: String) {
            synchronized(OUTPUT_LOCK) {
                val reader = ACTIVE_READER
                if (reader == null) {
                    kotlin.io.print(message)
                    return
                }
                val rendered = message.trimEnd('\n', '\r')
                if (rendered.isEmpty()) {
                    return
                }
                rendered.lineSequence().forEach { line ->
                    reader.printAbove(line)
                }
            }
        }
    }
}
