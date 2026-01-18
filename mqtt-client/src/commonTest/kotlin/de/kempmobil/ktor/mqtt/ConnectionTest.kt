package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionTest {

    private val mosquitto = "broker.hivemq.com"

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
    }

    @Test
    fun `test unencrypted connection`() = runTest {
        client = MqttClient(mosquitto, 1883) { }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `test TLS connection`() = runTest {
        client = MqttClient(mosquitto, 8883) {
            connection {
                tls { }
            }
        }
        val result = client.connect()
        if (result.isFailure) {
            result.exceptionOrNull()?.printStackTrace()
        }
        assertTrue(result.isSuccess)
    }
}