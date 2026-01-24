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
import com.hypixel.hytale.server.core.auth.ServerAuthManager
import com.hypixel.hytale.server.core.io.PacketHandler
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters
import com.hypixel.hytale.server.core.io.adapter.PacketFilter
import com.hypixel.hytale.server.core.io.transport.QUICTransport
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.Options
import org.slf4j.LoggerFactory
import ru.hytalemodding.lineage.backend.command.ProxyCommandBridge
import ru.hytalemodding.lineage.backend.config.BackendConfigLoader
import ru.hytalemodding.lineage.backend.control.BackendControlPlaneService
import ru.hytalemodding.lineage.backend.handshake.HandshakeInterceptor
import ru.hytalemodding.lineage.backend.messaging.BackendMessaging
import ru.hytalemodding.lineage.backend.security.ReplayProtector
import ru.hytalemodding.lineage.backend.security.TokenValidator
import ru.hytalemodding.lineage.shared.command.PlayerCommandProtocol
import ru.hytalemodding.lineage.shared.control.TokenValidationReason
import ru.hytalemodding.lineage.shared.control.TokenValidationResult
import ru.hytalemodding.lineage.shared.token.ProxyToken
import ru.hytalemodding.lineage.shared.token.TokenValidationError
import ru.hytalemodding.lineage.shared.token.TokenValidationException
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Backend mod that validates proxy referral tokens and exposes the fingerprint to the server.
 */
class LineageBackendMod(init: JavaPluginInit) : JavaPlugin(init) {
    private val logger = LoggerFactory.getLogger(LineageBackendMod::class.java)
    private lateinit var interceptor: HandshakeInterceptor
    private lateinit var config: ru.hytalemodding.lineage.backend.config.BackendConfig
    private val handshakeStates = ConcurrentHashMap<String, ProxyHandshakeState>()
    private var authModeAuthenticated = true
    private var authModeLabel = "unknown"
    private var enforceProxy = true
    private var useJavaAgentFallback = false
    private var commandBridgeEnabled = false
    private var commandBridge: ProxyCommandBridge? = null
    private var controlPlane: BackendControlPlaneService? = null

    override fun start() {
        super.start()
        logger.info("Lineage Backend Mod: Initializing with certificate forwarding support.")

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
            val replayProtector = ReplayProtector(
                config.replayWindowMillis,
                config.replayMaxEntries,
            )
            interceptor = HandshakeInterceptor(validator, config.serverId, replayProtector)
            resolveAuthMode()
            resolveSecurityMode()
            registerHandshakeBridge()

            eventRegistry.register(PlayerSetupConnectEvent::class.java, this::onPlayerConnect)
            if (!config.agentless && !config.enforceProxy) {
                logger.warn("Proxy enforcement disabled. Players can connect directly to this server.")
            }

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
                controlPlane = BackendControlPlaneService(config).also { it.start() }
                commandBridgeEnabled = true
                commandBridge = ProxyCommandBridge(this, { commandBridgeEnabled }).also {
                    it.start()
                    it.requestSync()
                }
                logger.info("Backend messaging connected to {}:{}", config.messagingHost, config.messagingPort)
            }

            logger.info("Lineage Backend Mod ready.")

        } catch (e: Exception) {
            logger.error("Failed to start mod", e)
        }
    }

    override fun shutdown() {
        commandBridge?.stop()
        controlPlane?.stop()
        BackendMessaging.stop()
        super.shutdown()
    }

    /**
     * Handles initial client connect and publishes proxy token validation.
     */
    private fun onPlayerConnect(event: PlayerSetupConnectEvent) {
        if (!authModeAuthenticated && config.requireAuthenticatedMode) {
            logger.warn("Rejecting {}: server auth-mode is {}", event.username, authModeLabel)
            event.reason = "Server requires AUTHENTICATED mode."
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
            val fingerprint = token.proxyCertB64?.let { resolveProxyFingerprint(it) }
            if (useJavaAgentFallback && fingerprint != null) {
                logger.info("Setting proxy fingerprint for {}: {}", event.username, fingerprint)
                System.setProperty("lineage.proxy.fingerprint", fingerprint)
            }
            controlPlane?.sendTokenValidationNotice(
                event.uuid,
                config.serverId,
                TokenValidationResult.ACCEPTED,
                null,
            )

        } catch (e: TokenValidationException) {
            logger.warn("Proxy token rejected for {}: {} ({})", event.username, e.error, e.message)
            controlPlane?.sendTokenValidationNotice(
                event.uuid,
                config.serverId,
                TokenValidationResult.REJECTED,
                mapTokenReason(e.error),
            )
            rejectIfEnforced(event)
        } catch (e: Exception) {
            logger.error("Bypass error", e)
            controlPlane?.sendTokenValidationNotice(
                event.uuid,
                config.serverId,
                TokenValidationResult.REJECTED,
                null,
            )
            rejectIfEnforced(event)
        }
    }

    private fun rejectIfEnforced(event: PlayerSetupConnectEvent) {
        if (!enforceProxy) {
            return
        }
        event.reason = "Proxy authentication failed."
        event.isCancelled = true
    }

    private fun validateReferralSource(event: PlayerSetupConnectEvent) {
        if (!event.isReferralConnection) {
            return
        }
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

    private fun resolveSecurityMode() {
        enforceProxy = config.enforceProxy || config.agentless
        useJavaAgentFallback = !config.agentless && config.javaAgentFallback
        if (config.agentless && config.javaAgentFallback) {
            logger.warn("Agentless enabled: javaagent_fallback is ignored.")
        }
        if (config.agentless && !config.enforceProxy) {
            logger.warn("Agentless enabled: enforce_proxy forced to true.")
        }
        if (config.javaAgentFallback) {
            logger.warn("JavaAgent fallback enabled (legacy mode).")
        }
    }

    private fun onCommandResponse(payload: ByteArray) {
        val response = PlayerCommandProtocol.decodeResponse(payload) ?: return
        val player = Universe.get().getPlayer(response.playerId) ?: return
        player.sendMessage(Message.raw(response.message))
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

    private fun resolveProxyFingerprint(certB64: String): String? {
        val cert = decodeClientCertificate(certB64)
        if (cert == null) {
            return certB64.takeIf { it.isNotBlank() }
        }
        return computeFingerprint(cert)
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

    private fun computeFingerprint(cert: X509Certificate): String? {
        val hash = runCatching { MessageDigest.getInstance("SHA-256").digest(cert.encoded) }.getOrNull() ?: return null
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private data class ProxyHandshakeState(
        val token: ProxyToken?,
        val error: TokenValidationException?,
    )
}
