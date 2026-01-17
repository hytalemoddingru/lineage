/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.util

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Generates self-signed certificates for the proxy QUIC listener.
 */
object CertificateUtil {
    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Pair of private key and certificate.
     */
    data class CertPair(val key: PrivateKey, val cert: X509Certificate)

    /**
     * Creates a self-signed EC certificate for the proxy.
     */
    fun generateSelfSigned(): CertPair {
        val keyGen = KeyPairGenerator.getInstance("EC", "BC")
        keyGen.initialize(256)
        val pair = keyGen.generateKeyPair()

        val notBefore = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val notAfter = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365))
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val name = X500Name("CN=Lineage Proxy")

        val builder = JcaX509v3CertificateBuilder(
            name, serial, notBefore, notAfter, name, pair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(pair.private)
        val cert = JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))

        return CertPair(pair.private, cert)
    }
}
