package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ConnectionTest {

    private val server = "broker.emqx.io"

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
    }

    @Test
    fun `test unencrypted connection`() = runTest {
        val port = 1883
        client = MqttClient(server, port) { }
        val result = client.connect()
        assertTrue(
            result.isSuccess,
            "Error connecting to ${server}:${port}\n${result.exceptionOrNull()?.stackTraceToString()}"
        )
    }

    @Test
    fun `test TLS connection`() = runTest {
        val port = 8883
        client = MqttClient(server, port) {
            connection {
                tls { }
            }
        }
        val result = client.connect()
        assertTrue(
            result.isSuccess,
            "Error connecting to ${server}:${port}\n${result.exceptionOrNull()?.stackTraceToString()}"
        )
    }
}