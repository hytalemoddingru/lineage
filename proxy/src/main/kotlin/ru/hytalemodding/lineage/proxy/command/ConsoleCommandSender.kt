/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType

/**
 * Console sender implementation.
 */
class ConsoleCommandSender : CommandSender {
    override val name: String = "console"
    override val type: SenderType = SenderType.CONSOLE

    override fun sendMessage(message: String) {
        println(message)
    }
}
