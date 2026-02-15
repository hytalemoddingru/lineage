/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.proxy.i18n.LocalizationRuntime
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class MessagesCommandTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun reloadReportsSuccessWhenFilesAreValid() {
        val runtime = LocalizationRuntime(
            messagesDir = tempDir.resolve("messages"),
            stylePath = tempDir.resolve("styles").resolve("rendering.toml"),
        )
        val command = MessagesCommand(runtime, runtime.messages)
        val sender = RecordingSender()

        command.execute(TestContext(sender, listOf("reload")))

        assertTrue(sender.messages.any { it.contains("reloaded", ignoreCase = true) || it.contains("перезагруж") })
    }

    @Test
    fun reloadReportsWarningsWhenStyleIsBroken() {
        val stylePath = tempDir.resolve("styles").resolve("rendering.toml")
        val runtime = LocalizationRuntime(
            messagesDir = tempDir.resolve("messages"),
            stylePath = stylePath,
        )
        Files.writeString(stylePath, "max_input_length = \"bad\"", StandardCharsets.UTF_8)
        val command = MessagesCommand(runtime, runtime.messages)
        val sender = RecordingSender()

        command.execute(TestContext(sender, listOf("reload")))

        assertTrue(sender.messages.any { it.contains("warnings", ignoreCase = true) || it.contains("предупреж") })
    }

    private data class TestContext(
        override val sender: CommandSender,
        override val args: List<String>,
    ) : CommandContext {
        override val input: String = "messages ${args.joinToString(" ")}".trim()
        override fun arg(index: Int): String? = args.getOrNull(index)
        override fun hasPermission(permission: String): Boolean = true
    }

    private class RecordingSender : CommandSender {
        override val name: String = "console"
        override val type: SenderType = SenderType.CONSOLE
        val messages = mutableListOf<String>()

        override fun sendMessage(message: String) {
            messages.add(message)
        }
    }
}
