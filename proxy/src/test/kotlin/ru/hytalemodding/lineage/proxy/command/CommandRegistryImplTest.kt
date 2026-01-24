/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.hytalemodding.lineage.api.command.Command
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandFlag

class CommandRegistryImplTest {
    @Test
    fun resolvesNamespacedAliases() {
        val registry = CommandRegistryImpl()
        val command = SimpleCommand("ping", listOf("p"))
        registry.register(command, "lineage")

        assertNotNull(registry.get("ping"))
        assertNotNull(registry.get("lineage:ping"))
        assertNotNull(registry.get("lineage:p"))
    }

    @Test
    fun resolvesModNamespace() {
        val registry = CommandRegistryImpl()
        val command = SimpleCommand("hub", emptyList())
        registry.register(command, "examplemod")

        assertNotNull(registry.get("examplemod:hub"))
    }

    private class SimpleCommand(
        override val name: String,
        override val aliases: List<String>,
    ) : Command {
        override val description: String = "test"
        override val usage: String = name
        override val permission: String? = null
        override val flags: Set<CommandFlag> = emptySet()

        override fun execute(context: CommandContext) = Unit

        override fun suggest(context: CommandContext): List<String> = emptyList()
    }
}
