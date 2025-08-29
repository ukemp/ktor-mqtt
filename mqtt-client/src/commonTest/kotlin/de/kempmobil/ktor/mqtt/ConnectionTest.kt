package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.test.runTest
import kotlin.test.*

@Ignore
class ConnectionTest {

    private val mosquitto = "test.mosquitto.org"

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
        client = MqttClient(mosquitto, 8886) {
            connection {
                tls { }
            }
        }
        val result = client.connect()
        assertTrue(result.isSuccess)
    }
}