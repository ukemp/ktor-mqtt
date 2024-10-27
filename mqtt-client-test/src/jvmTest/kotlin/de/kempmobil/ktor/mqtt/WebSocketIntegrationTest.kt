package de.kempmobil.ktor.mqtt

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

val NoTrustManager = object : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOf()
    override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
    override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
}

class WebSocketIntegrationTest : IntegrationTestBase() {

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
        client.close()
    }

    @Test
    fun `ws connection with credentials`() = runTest {
        client = createClient("http://${mosquitto.host}:${mosquitto.wsPort}")
        val connected = client.connect()

        assertTrue(connected.isSuccess)
    }

    @Test
    fun `wss connection with credentials`() = runTest {
        client = createClient("wss://${mosquitto.host}:${mosquitto.wssPort}")
        val connected = client.connect()

        assertTrue(connected.isSuccess)
    }

    private fun createClient(url: String): MqttClient {
        return MqttClient(Url(url)) {
            connection {
                http = {
                    HttpClient(CIO) {
                        install(WebSockets)
                        install(Logging)
                        engine {
                            https {
                                trustManager = NoTrustManager
                            }
                        }
                    }
                }
            }
            username = MosquittoContainer.user
            password = MosquittoContainer.password
        }
    }
}