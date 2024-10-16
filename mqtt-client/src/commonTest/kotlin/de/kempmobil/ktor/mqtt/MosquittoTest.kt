package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@Ignore
class MosquittoTest {

    private val host = "test.mosquitto.org"

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
    }

    @Test
    fun `test unencrypted connection`() = runTest {
        client = MqttClient(host) { }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `test TLS connection`() = runTest {
        client = MqttClient(host, 8886) {
            tls { }
        }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `publishing a sample packet`() = runTest {
        client = MqttClient(host) { }
        val result = client.connect()
        assertEquals(true, result.isSuccess)

        val qos = client.publish(buildPublishRequest("test/topic") {
            payload("This is a test publish packet")
            desiredQoS = QoS.EXACTLY_ONE
            userProperties {
                "user" to "property"
            }
        })
        assertEquals(true, qos.isSuccess)
        assertEquals(QoS.EXACTLY_ONE, qos.getOrThrow())
    }
}