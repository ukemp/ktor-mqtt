package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}