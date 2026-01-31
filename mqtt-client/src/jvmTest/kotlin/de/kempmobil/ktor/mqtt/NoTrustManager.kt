package de.kempmobil.ktor.mqtt

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Only for testing, trusts everything.
 */
object NoTrustManager : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}