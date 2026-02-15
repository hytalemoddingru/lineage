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
import ru.hytalemodding.lineage.api.command.CommandContext
import ru.hytalemodding.lineage.api.command.CommandSender
import ru.hytalemodding.lineage.api.command.SenderType
import ru.hytalemodding.lineage.api.messaging.Channel
import ru.hytalemodding.lineage.proxy.config.BackendConfig
import ru.hytalemodding.lineage.proxy.control.TransferRequestSender
import ru.hytalemodding.lineage.proxy.event.EventBusImpl
import ru.hytalemodding.lineage.proxy.net.BackendAvailabilityTracker
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.player.PlayerTransferService
import ru.hytalemodding.lineage.proxy.security.TransferTokenIssuer
import ru.hytalemodding.lineage.shared.control.TransferRequest
import java.util.UUID

class TransferCommandTest {
    @Test
    fun transferListShowsBackendStatuses() {
        val players = PlayerManagerImpl()
        val tracker = BackendAvailabilityTracker(knownBackendIds = setOf("hub", "survival"))
        tracker.markReportedOnline("hub")
        tracker.markReportedOffline("survival")
        val transferService = PlayerTransferService(
            eventBus = EventBusImpl(),
            requestSender = object : TransferRequestSender {
                override fun sendTransferRequest(request: TransferRequest): Boolean = true
            },
            tokenIssuer = TransferTokenIssuer("secret-123".toByteArray()),
            knownBackendIds = listOf("hub", "survival"),
            backendAvailabilityTracker = tracker,
        )
        val command = TransferCommand(
            players = players,
            transferService = transferService,
            backends = listOf(
                BackendConfig(id = "hub", host = "127.0.0.1", port = 25580),
                BackendConfig(id = "survival", host = "127.0.0.1", port = 25581),
            ),
            availabilityTracker = tracker,
        )
        val sender = RecordingSender("tester")

        command.execute(TestContext(sender, "transfer list", listOf("list")))

        assertTrue(sender.messages.any { it.contains("hub") && it.contains("ONLINE") })
        assertTrue(sender.messages.any { it.contains("survival") && it.contains("OFFLINE") })
    }

    @Test
    fun transferToOfflineBackendReturnsErrorWithoutSendingRequest() {
        val players = PlayerManagerImpl()
        val tracker = BackendAvailabilityTracker(knownBackendIds = setOf("hub", "survival"))
        tracker.markReportedOffline("survival")
        var sent = false
        val transferService = PlayerTransferService(
            eventBus = EventBusImpl(),
            requestSender = object : TransferRequestSender {
                override fun sendTransferRequest(request: TransferRequest): Boolean {
                    sent = true
                    return true
                }
            },
            tokenIssuer = TransferTokenIssuer("secret-123".toByteArray()),
            knownBackendIds = listOf("hub", "survival"),
            backendAvailabilityTracker = tracker,
        )
        val command = TransferCommand(
            players = players,
            transferService = transferService,
            backends = listOf(
                BackendConfig(id = "hub", host = "127.0.0.1", port = 25580),
                BackendConfig(id = "survival", host = "127.0.0.1", port = 25581),
            ),
            availabilityTracker = tracker,
        )

        val player = players.getOrCreate(UUID.randomUUID(), "tester")
        player.backendId = "hub"
        val sender = RecordingSender("tester")
        command.execute(TestContext(sender, "transfer survival tester", listOf("survival", "tester")))

        assertTrue(sender.messages.any { it.contains("offline", ignoreCase = true) })
        assertTrue(!sent)
    }

    @Test
    fun consoleRequiresPlayerNameForTransfer() {
        val players = PlayerManagerImpl()
        val tracker = BackendAvailabilityTracker(knownBackendIds = setOf("hub"))
        val transferService = PlayerTransferService(
            eventBus = EventBusImpl(),
            requestSender = object : TransferRequestSender {
                override fun sendTransferRequest(request: TransferRequest): Boolean = true
            },
            tokenIssuer = TransferTokenIssuer("secret-123".toByteArray()),
            knownBackendIds = listOf("hub"),
            backendAvailabilityTracker = tracker,
        )
        val command = TransferCommand(
            players = players,
            transferService = transferService,
            backends = listOf(BackendConfig(id = "hub", host = "127.0.0.1", port = 25580)),
            availabilityTracker = tracker,
        )
        val sender = RecordingSender("console", SenderType.CONSOLE)

        command.execute(TestContext(sender, "transfer hub", listOf("hub")))

        assertTrue(sender.messages.any { it.contains("Usage: transfer <backendId> <playerName>") })
    }

    @Test
    fun playerCanTransferSelfWithoutExplicitPlayerName() {
        val players = PlayerManagerImpl()
        val tracker = BackendAvailabilityTracker(knownBackendIds = setOf("hub", "survival"))
        tracker.markReportedOnline("hub")
        tracker.markReportedOnline("survival")
        var sent = false
        val transferService = PlayerTransferService(
            eventBus = EventBusImpl(),
            requestSender = object : TransferRequestSender {
                override fun sendTransferRequest(request: TransferRequest): Boolean {
                    sent = true
                    return true
                }
            },
            tokenIssuer = TransferTokenIssuer("secret-123".toByteArray()),
            knownBackendIds = listOf("hub", "survival"),
            backendAvailabilityTracker = tracker,
        )
        val command = TransferCommand(
            players = players,
            transferService = transferService,
            backends = listOf(
                BackendConfig(id = "hub", host = "127.0.0.1", port = 25580),
                BackendConfig(id = "survival", host = "127.0.0.1", port = 25581),
            ),
            availabilityTracker = tracker,
        )

        val player = players.getOrCreate(UUID.randomUUID(), "tester")
        player.backendId = "hub"
        val sender = ProxyPlayerCommandSender(
            player = player,
            responder = CommandResponder(TestChannel("response")),
        )

        command.execute(TestContext(sender, "transfer survival", listOf("survival")))

        assertTrue(sent)
    }

    private data class TestContext(
        override val sender: CommandSender,
        override val input: String,
        override val args: List<String>,
    ) : CommandContext {
        override fun arg(index: Int): String? = args.getOrNull(index)
        override fun hasPermission(permission: String): Boolean = true
    }

    private class RecordingSender(
        override val name: String,
        override val type: SenderType = SenderType.PLAYER,
    ) : CommandSender {
        val messages: MutableList<String> = mutableListOf()

        override fun sendMessage(message: String) {
            messages.add(message)
        }
    }

    private class TestChannel(
        override val id: String,
    ) : Channel {
        override fun send(payload: ByteArray) = Unit
    }
}
