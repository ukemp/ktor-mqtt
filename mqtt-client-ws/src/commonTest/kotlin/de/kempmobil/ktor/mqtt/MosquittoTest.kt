package de.kempmobil.ktor.mqtt

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@Ignore
class MosquittoTest {

    private val mosquitto = "test.mosquitto.org"

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
    }

    @Test
    fun `test unencrypted websocket connection`() = runTest {
        client = MqttWebSocketClient {
            connectTo(mosquitto, 8080) { }
        }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `test encrypted websocket connection`() = runTest {
        client = MqttWebSocketClient {
            connectTo(mosquitto, 8081) {
                useWss = true
            }
        }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `test encrypted websocket connection with http proxy`() = runTest {
        client = MqttWebSocketClient {
            connectTo(mosquitto, 8081) {
                clientFactory = {
                    HttpClient(CIO) {
                        install(WebSockets)
                        engine {
                            proxy = ProxyBuilder.http("http://sia-lb.telekom.de:8080")
                        }
                    }
                }
                useWss = true
            }
        }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }
}