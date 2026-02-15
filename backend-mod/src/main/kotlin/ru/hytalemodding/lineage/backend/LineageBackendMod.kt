/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend

import com.hypixel.hytale.protocol.HostAddress
import com.hypixel.hytale.protocol.packets.connection.Connect
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent
import com.hypixel.hytale.server.core.event.events.ShutdownEvent
import com.hypixel.hytale.server.core.auth.ServerAuthManager
import com.hypixel.hytale.server.core.io.PacketHandler
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters
import com.hypixel.hytale.server.core.io.adapter.PacketFilter
import com.hypixel.hytale.server.core.io.transport.QUICTransport
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.Options
import org.slf4j.LoggerFactory
import ru.hytalemodding.lineage.backend.auth.AuthModePolicy
import ru.hytalemodding.lineage.backend.command.ProxyCommandBridge
import ru.hytalemodding.lineage.backend.config.BackendConfigLoader
import ru.hytalemodding.lineage.backend.control.BackendControlPlaneService
import ru.hytalemodding.lineage.backend.handshake.HandshakeInterceptor
import ru.hytalemodding.lineage.backend.messaging.BackendMessaging
import ru.hytalemodding.lineage.backend.message.LegacyColorMessageRenderer
import ru.hytalemodding.lineage.backend.security.ReplayProtector
import ru.hytalemodding.lineage.backend.security.TokenValidator
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol
import ru.hytalemodding.lineage.shared.control.ControlMessageType
import ru.hytalemodding.lineage.shared.control.ControlProtocol
import ru.hytalemodding.lineage.shared.control.ControlReplayProtector
import ru.hytalemodding.lineage.shared.control.TokenValidationReason
import ru.hytalemodding.lineage.shared.control.TokenValidationResult
import ru.hytalemodding.lineage.shared.logging.StructuredLog
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.TokenValidationError
import ru.hytalemodding.lineage.shared.token.TokenValidationException
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.net.InetSocketAddress
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Backend mod that validates proxy referral tokens and exposes the fingerprint to the server.
 */
class LineageBackendMod(init: JavaPluginInit) : JavaPlugin(init) {
    companion object {
        private const val SHUTDOWN_REROUTE_PRIORITY_OFFSET = 64
        private const val SHUTDOWN_REROUTE_GRACE_MILLIS = 1_200L
        private const val SHUTDOWN_REFERRAL_ATTEMPTS = 3
        private const val SHUTDOWN_REFERRAL_INTERVAL_MILLIS = 40L
    }

    private val logger = LoggerFactory.getLogger(LineageBackendMod::class.java)
    private lateinit var interceptor: HandshakeInterceptor
    private lateinit var config: ru.hytalemodding.lineage.backend.config.BackendConfig
    private val handshakeStates = ConcurrentHashMap<String, ProxyHandshakeState>()
    private var authModeAuthenticated = true
    private var authModeLabel = "unknown"
    private lateinit var commandReplayProtector: ControlReplayProtector
    private var commandBridgeEnabled = false
    private var commandBridge: ProxyCommandBridge? = null
    private var controlPlane: BackendControlPlaneService? = null
    private val shutdownRerouteTriggered = AtomicBoolean(false)

    override fun start() {
        super.start()
        logger.info("Lineage Backend Mod: Initializing with certificate forwarding support.")

        try {
            val configPath = dataDirectory.resolve("config.toml")
            val bootstrap = BackendConfigLoader.ensureConfig(configPath, "server-1")
            config = bootstrap.config
            commandReplayProtector = ControlReplayProtector(
                config.controlReplayWindowMillis,
                config.controlReplayMaxEntries,
            )

            val secrets = mutableListOf<ByteArray>()
            secrets.add(config.proxySecret.toByteArray(StandardCharsets.UTF_8))
            config.previousProxySecret?.let { previous ->
                secrets.add(previous.toByteArray(StandardCharsets.UTF_8))
            }

            val validator = TokenValidator(secrets)
            val replayProtector = ReplayProtector(
                config.replayWindowMillis,
                config.replayMaxEntries,
            )
            interceptor = HandshakeInterceptor(validator, config.serverId, replayProtector)
            resolveAuthMode()
            registerHandshakeBridge()

            eventRegistry.register(PlayerSetupConnectEvent::class.java, this::onPlayerConnect)
            eventRegistry.register(
                (ShutdownEvent.DISCONNECT_PLAYERS - SHUTDOWN_REROUTE_PRIORITY_OFFSET).toShort(),
                ShutdownEvent::class.java,
            ) { handleShutdownDisconnectPlayers(it) }

            if (!authModeAuthenticated) {
                if (config.requireAuthenticatedMode) {
                    logger.error(
                        "Unsupported auth-mode {}. AUTHENTICATED is required (set require_authenticated_mode=false to override).",
                        authModeLabel,
                    )
                } else {
                    logger.warn("Running with auth-mode {}. AUTHENTICATED is recommended.", authModeLabel)
                }
            }

            if (config.messagingEnabled) {
                val address = InetSocketAddress(config.messagingHost, config.messagingPort)
                BackendMessaging.start(address, secrets.first())
                BackendMessaging.registerChannel(PlayerCommandProtocol.RESPONSE_CHANNEL_ID, this::onCommandResponse)
                BackendMessaging.registerChannel(PlayerCommandProtocol.SYSTEM_RESPONSE_CHANNEL_ID, this::onSystemResponse)
                controlPlane = BackendControlPlaneService(config).also { it.start() }
                commandBridgeEnabled = true
                commandBridge = ProxyCommandBridge(this, { commandBridgeEnabled }).also {
                    it.start()
                }
                logger.info("Backend messaging connected to {}:{}", config.messagingHost, config.messagingPort)
            }

            logger.info("Lineage Backend Mod ready.")

        } catch (e: Exception) {
            logger.error("Failed to start mod", e)
        }
    }

    override fun shutdown() {
        referPlayersToProxyOnShutdown()
        commandBridge?.stop()
        controlPlane?.stop()
        BackendMessaging.stop()
        super.shutdown()
    }

    private fun handleShutdownDisconnectPlayers(@Suppress("UNUSED_PARAMETER") event: ShutdownEvent?) {
        logger.info("Shutdown phase before player disconnect: rerouting players to proxy")
        controlPlane?.announceOffline()
        val reroutedPlayers = referPlayersToProxyOnShutdown()
        if (reroutedPlayers > 0) {
            runCatching { Thread.sleep(SHUTDOWN_REROUTE_GRACE_MILLIS) }
        }
    }

    private fun referPlayersToProxyOnShutdown(): Int {
        if (!shutdownRerouteTriggered.compareAndSet(false, true)) {
            return 0
        }
        if (!::config.isInitialized) {
            return 0
        }
        val players = runCatching { Universe.get().players }.getOrNull() ?: return 0
        if (players.isEmpty()) {
            return 0
        }
        logger.info("Rerouting {} player(s) to proxy before backend shutdown", players.size)
        val emptyReferral = ByteArray(0)
        players.forEach { player ->
            runCatching {
                repeat(SHUTDOWN_REFERRAL_ATTEMPTS) { attempt ->
                    player.referToServer(config.proxyConnectHost, config.proxyConnectPort, emptyReferral)
                    player.packetHandler.tryFlush()
                    if (attempt + 1 < SHUTDOWN_REFERRAL_ATTEMPTS) {
                        Thread.sleep(SHUTDOWN_REFERRAL_INTERVAL_MILLIS)
                    }
                }
            }.onFailure { error ->
                logger.warn("Failed to reroute player {} during shutdown", player.uuid, error)
            }
        }
        return players.size
    }

    /**
     * Handles initial client connect and publishes proxy token validation.
     */
    private fun onPlayerConnect(event: PlayerSetupConnectEvent) {
        if (AuthModePolicy.shouldRejectHandshake(config.requireAuthenticatedMode, authModeAuthenticated)) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "handshake",
                    severity = "WARN",
                    reason = "AUTH_MODE_REJECT",
                    correlationId = event.uuid.toString(),
                    fields = mapOf(
                        "username" to event.username,
                        "authMode" to authModeLabel,
                    ),
                )
            )
            event.reason = AuthModePolicy.REQUIRED_AUTH_MESSAGE
            event.isCancelled = true
            controlPlane?.sendTokenValidationNotice(
                event.uuid,
                config.serverId,
                TokenValidationResult.REJECTED,
                null,
            )
            return
        }
        try {
            val state = takeHandshakeState(event.packetHandler)
            val token = when {
                state?.token != null -> state.token
                state?.error != null -> throw state.error
                else -> {
                    validateReferralSource(event)
                    interceptor.validateReferralData(event.referralData)
                }
            }
            ensureCertificatesApplied(event.packetHandler, token)
            controlPlane?.sendTokenValidationNotice(
                event.uuid,
                config.serverId,
                TokenValidationResult.ACCEPTED,
                null,
            )
        } catch (e: TokenValidationException) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "handshake",
                    severity = "WARN",
                    reason = "PROXY_TOKEN_REJECTED",
                    correlationId = event.uuid.toString(),
                    fields = mapOf(
                        "username" to event.username,
                        "validationError" to e.error.name,
                        "details" to (e.message ?: "none"),
                    ),
                )
            )
            controlPlane?.sendTokenValidationNotice(
                event.uuid,
                config.serverId,
                TokenValidationResult.REJECTED,
                mapTokenReason(e.error),
            )
            rejectIfEnforced(event)
        } catch (e: Exception) {
            logger.error(
                "{}",
                StructuredLog.event(
                    category = "handshake",
                    severity = "ERROR",
                    reason = "HANDSHAKE_BYPASS_ERROR",
                    correlationId = event.uuid.toString(),
                    fields = mapOf("username" to event.username, "errorType" to e.javaClass.simpleName),
                ),
                e,
            )
            controlPlane?.sendTokenValidationNotice(
                event.uuid,
                config.serverId,
                TokenValidationResult.REJECTED,
                null,
            )
            rejectIfEnforced(event)
        }
    }

    private fun ensureCertificatesApplied(packetHandler: PacketHandler, token: ProxyToken) {
        if (!applyClientCertificate(packetHandler, token)) {
            throw TokenValidationException(TokenValidationError.MALFORMED, "Missing client certificate")
        }
        if (!applyServerCertificate(token)) {
            throw TokenValidationException(TokenValidationError.MALFORMED, "Missing proxy certificate")
        }
    }

    private fun rejectIfEnforced(event: PlayerSetupConnectEvent) {
        event.reason = "Proxy authentication failed."
        event.isCancelled = true
    }

    private fun validateReferralSource(event: PlayerSetupConnectEvent) {
        validateReferralSource(event.referralSource)
    }

    private fun validateReferralSource(source: HostAddress?) {
        val referralSource = source
            ?: throw TokenValidationException(TokenValidationError.MALFORMED, "Missing referral source")
        val expectedHost = config.referralSourceHost
        val expectedPort = config.referralSourcePort
        val hostMatches = referralSource.host.equals(expectedHost, ignoreCase = true)
        val portMatches = referralSource.port.toInt() == expectedPort
        if (!hostMatches || !portMatches) {
            throw TokenValidationException(
                TokenValidationError.MALFORMED,
                "Unexpected referral source ${referralSource.host}:${referralSource.port}",
            )
        }
    }

    private fun resolveAuthMode() {
        val optionSet = Options.getOptionSet()
        val mode = optionSet?.valueOf(Options.AUTH_MODE)
        authModeLabel = mode?.name ?: "unknown"
        authModeAuthenticated = mode == Options.AuthMode.AUTHENTICATED
    }

    private fun onCommandResponse(payload: ByteArray) {
        val response = decodeValidatedResponse(payload, "COMMAND_RESPONSE") ?: return
        val player = Universe.get().getPlayer(response.playerId) ?: return
        player.sendMessage(LegacyColorMessageRenderer.render(response.message))
    }

    private fun onSystemResponse(payload: ByteArray) {
        val response = decodeValidatedResponse(payload, "SYSTEM_RESPONSE") ?: return
        val player = Universe.get().getPlayer(response.playerId) ?: return
        player.sendMessage(LegacyColorMessageRenderer.render(response.message))
    }

    private fun decodeValidatedResponse(
        payload: ByteArray,
        reasonPrefix: String,
    ): ru.hytalemodding.lineage.shared.command.PlayerCommandResponse? {
        val version = PlayerCommandProtocol.peekVersion(payload)
        if (version == null) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "control-plane",
                    severity = "WARN",
                    reason = "${reasonPrefix}_MALFORMED",
                )
            )
            return null
        }
        if (!PlayerCommandProtocol.hasSupportedVersion(payload)) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "control-plane",
                    severity = "WARN",
                    reason = "${reasonPrefix}_VERSION_MISMATCH",
                    fields = mapOf("version" to version),
                )
            )
            return null
        }
        val response = PlayerCommandProtocol.decodeResponse(payload) ?: run {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "control-plane",
                    severity = "WARN",
                    reason = "${reasonPrefix}_MALFORMED",
                )
            )
            return null
        }
        if (!ControlProtocol.isTimestampValid(
                response.issuedAtMillis,
                response.ttlMillis,
                System.currentTimeMillis(),
                config.controlMaxSkewMillis,
            )
        ) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "control-plane",
                    severity = "WARN",
                    reason = "${reasonPrefix}_INVALID_TIMESTAMP",
                    correlationId = response.playerId.toString(),
                )
            )
            return null
        }
        if (!commandReplayProtector.tryRegister("proxy", ControlMessageType.TRANSFER_RESULT, response.nonce)) {
            logger.warn(
                "{}",
                StructuredLog.event(
                    category = "control-plane",
                    severity = "WARN",
                    reason = "${reasonPrefix}_REPLAYED",
                    correlationId = response.playerId.toString(),
                )
            )
            return null
        }
        return response
    }

    private fun mapTokenReason(error: TokenValidationError): TokenValidationReason? {
        return when (error) {
            TokenValidationError.MALFORMED -> TokenValidationReason.MALFORMED
            TokenValidationError.INVALID_SIGNATURE -> TokenValidationReason.INVALID_SIGNATURE
            TokenValidationError.EXPIRED -> TokenValidationReason.EXPIRED
            TokenValidationError.NOT_YET_VALID -> TokenValidationReason.NOT_YET_VALID
            TokenValidationError.UNSUPPORTED_VERSION -> TokenValidationReason.UNSUPPORTED_VERSION
            TokenValidationError.TARGET_MISMATCH -> TokenValidationReason.TARGET_MISMATCH
            TokenValidationError.REPLAYED -> TokenValidationReason.REPLAYED
        }
    }

    private fun registerHandshakeBridge() {
        val filter = PacketFilter { packetHandler, packet ->
            if (packet !is Connect) {
                return@PacketFilter false
            }
            val channel = packetHandler.channel
            val key = channel.id().asShortText()
            if (handshakeStates.containsKey(key)) {
                return@PacketFilter false
            }
            val state = try {
                validateReferralSource(packet.referralSource)
                val token = interceptor.validateReferralData(packet.referralData)
                if (!applyClientCertificate(packetHandler, token)) {
                    throw TokenValidationException(TokenValidationError.MALFORMED, "Missing client certificate")
                }
                if (!applyServerCertificate(token)) {
                    throw TokenValidationException(TokenValidationError.MALFORMED, "Missing proxy certificate")
                }
                ProxyHandshakeState(token, null)
            } catch (e: TokenValidationException) {
                ProxyHandshakeState(null, e)
            } catch (e: Exception) {
                ProxyHandshakeState(null, TokenValidationException(TokenValidationError.MALFORMED, e.message ?: "Proxy token error"))
            }
            handshakeStates[key] = state
            channel.closeFuture().addListener { handshakeStates.remove(key) }
            false
        }
        PacketAdapters.registerInbound(filter)
    }

    private fun takeHandshakeState(packetHandler: PacketHandler): ProxyHandshakeState? {
        val key = packetHandler.channel.id().asShortText()
        return handshakeStates.remove(key)
    }

    private fun applyClientCertificate(packetHandler: PacketHandler, token: ProxyToken): Boolean {
        val certB64 = token.clientCertB64 ?: return false
        val cert = decodeClientCertificate(certB64) ?: run {
            logger.warn("Proxy token did not include a valid client certificate for {}", packetHandler.identifier)
            return false
        }
        packetHandler.channel.attr(QUICTransport.CLIENT_CERTIFICATE_ATTR).set(cert)
        return true
    }

    private fun applyServerCertificate(token: ProxyToken): Boolean {
        val certB64 = token.proxyCertB64 ?: return false
        val cert = decodeClientCertificate(certB64) ?: run {
            logger.warn("Proxy token did not include a valid proxy certificate")
            return false
        }
        ServerAuthManager.getInstance().setServerCertificate(cert)
        return true
    }

    private fun decodeClientCertificate(certB64: String): X509Certificate? {
        if (certB64.isBlank()) {
            return null
        }
        val bytes = runCatching { Base64.getUrlDecoder().decode(certB64) }.getOrNull() ?: return null
        return runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        }.getOrNull()
    }

    private data class ProxyHandshakeState(
        val token: ProxyToken?,
        val error: TokenValidationException?,
    )
}
