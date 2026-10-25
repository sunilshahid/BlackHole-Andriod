package com.blackhole.app.network

import android.content.Context
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Manages persistent self-signed TLS certificates and device authentication fingerprints.
 */
object TlsHelper {
    private var cachedHeldCertificate: HeldCertificate? = null
    private var cachedFingerprint: String? = null
    private var cachedSslContext: SSLContext? = null
    private var cachedSocketFactory: SSLSocketFactory? = null

    @Synchronized
    fun getOrCreateHeldCertificate(context: Context? = null): HeldCertificate {
        cachedHeldCertificate?.let { return it }

        if (context != null) {
            try {
                val certFile = File(context.filesDir, "device_cert.pem")
                if (certFile.exists() && certFile.length() > 0) {
                    val pem = certFile.readText(Charsets.UTF_8)
                    val decoded = HeldCertificate.decode(pem)
                    cachedHeldCertificate = decoded
                    cachedFingerprint = computeFingerprint(decoded.certificate)
                    return decoded
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Generate a new self-signed certificate with long validity
        val newCert = HeldCertificate.Builder()
            .commonName("BlackHole Device")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()

        cachedHeldCertificate = newCert
        cachedFingerprint = computeFingerprint(newCert.certificate)

        if (context != null) {
            try {
                val certFile = File(context.filesDir, "device_cert.pem")
                val combinedPem = "${newCert.certificatePem()}\n${newCert.privateKeyPkcs8Pem()}"
                certFile.writeText(combinedPem, Charsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return newCert
    }

    @Synchronized
    fun getDeviceFingerprint(context: Context? = null): String {
        cachedFingerprint?.let { return it }
        val cert = getOrCreateHeldCertificate(context)
        val fp = computeFingerprint(cert.certificate)
        cachedFingerprint = fp
        return fp
    }

    private fun computeFingerprint(cert: X509Certificate): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert.encoded)
            digest.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            // Fallback 64-char hex string
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest("blackhole-android-cert-fallback".toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02X".format(it) }
        }
    }

    @Synchronized
    fun getOrCreateSslContext(context: Context? = null): SSLContext? {
        if (cachedSslContext != null) return cachedSslContext
        try {
            val heldCertificate = getOrCreateHeldCertificate(context)

            val handshakeCertificates = HandshakeCertificates.Builder()
                .heldCertificate(heldCertificate)
                .build()

            val ctx = handshakeCertificates.sslContext()
            cachedSslContext = ctx
            cachedSocketFactory = handshakeCertificates.sslSocketFactory()
            return ctx
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private var trustAllSslContext: SSLContext? = null

    @Synchronized
    fun getTrustAllSSLSocketFactory(): SSLSocketFactory {
        trustAllSslContext?.let { return it.socketFactory }
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAllCerts, java.security.SecureRandom())
        trustAllSslContext = ctx
        return ctx.socketFactory
    }
}
