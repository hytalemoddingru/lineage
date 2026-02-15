/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net

import io.netty.handler.ssl.util.SimpleTrustManagerFactory
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Trust manager for inbound client certificate capture on proxy listener.
 *
 * The authenticated flow is enforced by downstream token/JWT validation.
 * Here we allow presented client certificates so the proxy can forward
 * certificate binding data to backend without CA-chain requirements.
 */
object ClientCertificateCaptureTrustManagerFactory : SimpleTrustManagerFactory() {
    override fun engineInit(keyStore: KeyStore?) = Unit

    override fun engineInit(managerFactoryParameters: ManagerFactoryParameters?) = Unit

    override fun engineGetTrustManagers(): Array<TrustManager> {
        return arrayOf(ClientCertificateCaptureTrustManager)
    }
}

private object ClientCertificateCaptureTrustManager : X509ExtendedTrustManager() {
    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit

    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?, socket: Socket?) = Unit

    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?, socket: Socket?) = Unit

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?, engine: SSLEngine?) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
