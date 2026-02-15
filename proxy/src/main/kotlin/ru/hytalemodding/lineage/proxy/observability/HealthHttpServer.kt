/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.observability

import com.sun.net.httpserver.HttpServer
import ru.hytalemodding.lineage.proxy.config.ObservabilityConfig
import ru.hytalemodding.lineage.proxy.util.Logging
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HealthHttpServer(
    private val config: ObservabilityConfig,
    private val evaluator: ProxyHealthEvaluator,
    private val metricsProvider: (() -> String)? = null,
    private val statusProvider: (() -> String)? = null,
) : AutoCloseable {
    private val logger = Logging.logger(HealthHttpServer::class.java)
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    fun start() {
        if (!config.enabled) {
            logger.info("Observability HTTP endpoint is disabled")
            return
        }
        val boundServer = HttpServer.create(InetSocketAddress(config.host, config.port), 0)
        val boundExecutor = Executors.newSingleThreadExecutor()
        boundServer.executor = boundExecutor
        boundServer.createContext("/health") { exchange ->
            val snapshot = evaluator.snapshot()
            val body = healthPayload(snapshot).toByteArray(StandardCharsets.UTF_8)
            val code = if (snapshot.status == HealthStatus.FAILED) 503 else 200
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(code, body.size.toLong())
            exchange.responseBody.use { output -> output.write(body) }
        }
        metricsProvider?.let { provider ->
            boundServer.createContext("/metrics") { exchange ->
                val body = provider().toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { output -> output.write(body) }
            }
        }
        statusProvider?.let { provider ->
            boundServer.createContext("/status") { exchange ->
                val body = provider().toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { output -> output.write(body) }
            }
        }
        boundServer.start()
        server = boundServer
        executor = boundExecutor
        logger.info("Observability HTTP endpoint listening on {}:{}", config.host, config.port)
    }

    override fun close() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
    }

    private fun healthPayload(snapshot: HealthSnapshot): String {
        return """
            {"status":"${snapshot.status}","checks":{"listener":"${snapshot.listener}","messaging":"${snapshot.messaging}"}}
        """.trimIndent()
    }
}
