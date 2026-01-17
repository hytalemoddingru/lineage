/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend

import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.universe.Universe
import org.slf4j.LoggerFactory
import ru.hytalemodding.lineage.backend.command.LineageProxyCommand
import ru.hytalemodding.lineage.backend.config.BackendConfigLoader
import ru.hytalemodding.lineage.backend.handshake.HandshakeInterceptor
import ru.hytalemodding.lineage.backend.messaging.BackendMessaging
import ru.hytalemodding.lineage.backend.security.TokenValidator
import ru.hytalemodding.lineage.backend.transfer.TransferTokenIssuer
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol
import ru.hytalemodding.lineage.shared.token.TokenValidationException
import java.nio.charset.StandardCharsets
import java.net.InetSocketAddress

/**
 * Backend mod that validates proxy referral tokens and exposes the fingerprint to the server.
 */
class LineageBackendMod(init: JavaPluginInit) : JavaPlugin(init) {
    private val logger = LoggerFactory.getLogger(LineageBackendMod::class.java)
    private lateinit var interceptor: HandshakeInterceptor
    private lateinit var config: ru.hytalemodding.lineage.backend.config.BackendConfig
    private lateinit var transferIssuer: TransferTokenIssuer
    private var commandBridgeEnabled = false

    override fun start() {
        super.start()
        logger.info("Lineage Backend Mod: Initializing with Fingerprint Forwarding support.")

        try {
            val configPath = dataDirectory.resolve("config.toml")
            val bootstrap = BackendConfigLoader.ensureConfig(configPath, "server-1")
            config = bootstrap.config

            val secrets = mutableListOf<ByteArray>()
            secrets.add(config.proxySecret.toByteArray(StandardCharsets.UTF_8))
            config.previousProxySecret?.let { previous ->
                secrets.add(previous.toByteArray(StandardCharsets.UTF_8))
            }

            val validator = TokenValidator(secrets)
            interceptor = HandshakeInterceptor(validator, config.serverId)
            transferIssuer = TransferTokenIssuer(secrets.first())

            eventRegistry.register(PlayerSetupConnectEvent::class.java, this::onPlayerConnect)
            commandRegistry.registerCommand(
                LineageProxyCommand(
                    { commandBridgeEnabled },
                    transferIssuer,
                    config.proxyConnectHost,
                    config.proxyConnectPort,
                )
            )

            if (!config.enforceProxy) {
                logger.warn("Proxy enforcement disabled. Players can connect directly to this server.")
            }

            if (config.messagingEnabled) {
                val address = InetSocketAddress(config.messagingHost, config.messagingPort)
                BackendMessaging.start(address, secrets.first())
                BackendMessaging.registerChannel(PlayerCommandProtocol.RESPONSE_CHANNEL_ID, this::onCommandResponse)
                commandBridgeEnabled = true
                logger.info("Backend messaging connected to {}:{}", config.messagingHost, config.messagingPort)
            }

            logger.info("Lineage Backend Mod ready.")

        } catch (e: Exception) {
            logger.error("Failed to start mod", e)
        }
    }

    override fun shutdown() {
        BackendMessaging.stop()
        super.shutdown()
    }

    /**
     * Handles initial client connect and publishes proxy fingerprint to the server.
     */
    private fun onPlayerConnect(event: PlayerSetupConnectEvent) {
        try {
            val token = interceptor.validateReferralData(event.referralData)
            val fingerprint = token.clientCertB64

            if (fingerprint != null) {
                logger.info("Setting proxy fingerprint for {}: {}", event.username, fingerprint)
                System.setProperty("lineage.proxy.fingerprint", fingerprint)
            }

        } catch (e: TokenValidationException) {
            if (config.enforceProxy) {
                logger.warn("Rejecting {}: {}", event.username, e.message)
                event.reason = "Proxy authentication failed."
                event.isCancelled = true
            }
        } catch (e: Exception) {
            logger.error("Bypass error", e)
            if (config.enforceProxy) {
                event.reason = "Proxy authentication failed."
                event.isCancelled = true
            }
        }
    }

    private fun onCommandResponse(payload: ByteArray) {
        val response = PlayerCommandProtocol.decodeResponse(payload) ?: return
        val player = Universe.get().getPlayer(response.playerId) ?: return
        player.sendMessage(Message.raw(response.message))
    }
}
