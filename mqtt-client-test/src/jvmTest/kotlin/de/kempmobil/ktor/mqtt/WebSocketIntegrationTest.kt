package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.ws.MqttClient
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

open class WebSocketIntegrationTest : IntegrationTestBase() {

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
        client.close()
    }

    @Test
    fun `websocket connection with CIO client`() = runTest {
        client = createClient("http://${mosquitto.host}:${mosquitto.wsPort}", CIO)
        val connected = client.connect()

        assertTrue(connected.isSuccess)
        assertTrue(connected.getOrThrow().isSuccess)
    }

    @Test
    fun `websocket connection with OkHttp client`() = runTest {
        client = createClient("http://${mosquitto.host}:${mosquitto.wsPort}", OkHttp)
        val connected = client.connect()

        assertTrue(connected.isSuccess)
        assertTrue(connected.getOrThrow().isSuccess)
    }

    @Test
    fun `websocket connection with Java client`() = runTest {
        client = createClient("http://${mosquitto.host}:${mosquitto.wsPort}", Java)
        val connected = client.connect()

        assertTrue(connected.isSuccess)
        assertTrue(connected.getOrThrow().isSuccess)
    }

    @Test
    fun `websocket TLS connection with CIO client`() = runTest {
        val url = "wss://${mosquitto.host}:${mosquitto.wssPort}"
        client = MqttClient(url) {
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
            username = MosquittoContainer.USER
            password = MosquittoContainer.PASSWORD
        }
        val connected = client.connect()

        assertTrue(connected.isSuccess)
        assertTrue(connected.getOrThrow().isSuccess)

        client.disconnect()
        val reconnected = client.connect()

        assertTrue(reconnected.isSuccess)
        assertTrue(reconnected.getOrThrow().isSuccess)
    }

    private fun createClient(url: String, engineFactory: HttpClientEngineFactory<*>): MqttClient {
        return MqttClient(url) {
            connection {
                http = {
                    HttpClient(engineFactory) {
                        install(WebSockets)
                        install(Logging)
                    }
                }
            }
            username = MosquittoContainer.USER
            password = MosquittoContainer.PASSWORD
        }
    }
}