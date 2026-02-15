/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.logging

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ru.hytalemodding.lineage.proxy.console.ConsoleInputService

/**
 * Writes logs above active console prompt when the interactive shell is running.
 */
class InteractiveConsoleAppender : AppenderBase<ILoggingEvent>() {
    var pattern: String = "%d{HH:mm:ss.SSS} %-5level [%logger{0}] %msg%n"
    private var layout: PatternLayout? = null

    override fun start() {
        val activeLayout = PatternLayout().also {
            it.context = context
            it.pattern = pattern
            it.start()
        }
        layout = activeLayout
        super.start()
    }

    override fun stop() {
        layout?.stop()
        layout = null
        super.stop()
    }

    override fun append(eventObject: ILoggingEvent) {
        val rendered = layout?.doLayout(eventObject) ?: return
        ConsoleInputService.printOutput(rendered)
    }
}
