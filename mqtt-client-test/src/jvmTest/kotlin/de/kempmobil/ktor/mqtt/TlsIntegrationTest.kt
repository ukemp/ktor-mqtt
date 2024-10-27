package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TlsIntegrationTest : IntegrationTestBase() {

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
        client.close()
    }

    @Test
    fun `connection via TLS`() = runTest {
        client = MqttClient(mosquitto.host, mosquitto.tlsPort) {
            connection {
                tls {
                    // Don't check certificates:
                    trustManager = object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOf()
                        override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                    }
                }
            }
            username = MosquittoContainer.user
            password = MosquittoContainer.password
        }

        assertEquals(Disconnected, client.connectionState.first())
        val result = client.connect()

        assertEquals(Connected(result.getOrThrow()), client.connectionState.first())
    }
}