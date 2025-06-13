package com.timetracking.app.core.utils


import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

object TrustAllCerts {
    fun createSSLSocketFactory(): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustManager), SecureRandom())
        return context.socketFactory
    }

    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
}