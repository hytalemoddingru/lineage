/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.command

import ru.hytalemodding.lineage.api.command.CommandFlag
import ru.hytalemodding.lineage.api.messaging.MessageHandler
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.proxy.util.Logging
import ru.hytalemodding.lineage.shared.command.ProxyCommandDescriptor
import ru.hytalemodding.lineage.shared.command.ProxyCommandFlags
import ru.hytalemodding.lineage.shared.command.ProxyCommandRegistryProtocol

class CommandRegistrySyncService(
    private val registry: CommandRegistryImpl,
    private val messaging: Messaging,
) {
    private val logger = Logging.logger(CommandRegistrySyncService::class.java)
    private val outputChannel = messaging.channel(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID)
        ?: messaging.registerChannel(ProxyCommandRegistryProtocol.REGISTRY_CHANNEL_ID, MessageHandler { })

    init {
        messaging.registerChannel(ProxyCommandRegistryProtocol.REQUEST_CHANNEL_ID, MessageHandler { message ->
            if (!ProxyCommandRegistryProtocol.decodeRequest(message.payload)) {
                return@MessageHandler
            }
            sendSnapshot()
        })
        registry.addListener { sendSnapshot() }
    }

    fun sendSnapshot() {
        val descriptors = registry.snapshot()
            .sortedWith(compareBy<CommandEntry>({ it.ownerId }, { it.baseNames.firstOrNull() ?: "" }))
            .map { entry -> entry.toDescriptor() }
        val payload = ProxyCommandRegistryProtocol.encodeSnapshot(descriptors)
        if (payload.size > MAX_PAYLOAD_BYTES) {
            logger.warn("Command registry payload too large ({} bytes), skipping sync.", payload.size)
            return
        }
        outputChannel.send(payload)
    }

    private fun CommandEntry.toDescriptor(): ProxyCommandDescriptor {
        return ProxyCommandDescriptor(
            namespace = ownerId,
            name = baseNames.first(),
            aliases = baseNames.drop(1),
            description = command.description,
            usage = command.usage,
            permission = command.permission,
            flags = encodeFlags(command.flags),
        )
    }

    private fun encodeFlags(flags: Set<CommandFlag>): Int {
        var mask = 0
        if (CommandFlag.PLAYER_ONLY in flags) {
            mask = mask or ProxyCommandFlags.PLAYER_ONLY
        }
        if (CommandFlag.HIDDEN in flags) {
            mask = mask or ProxyCommandFlags.HIDDEN
        }
        return mask
    }

    private companion object {
        private const val MAX_PAYLOAD_BYTES = 60_000
    }
}
