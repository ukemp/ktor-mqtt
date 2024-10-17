package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

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
        assertTrue(result.isSuccess)
    }

    @Test
    fun `publishing a sample packet`() = runTest {
        client = MqttClient(host) { }
        val result = client.connect()
        assertTrue(result.isSuccess)

        val qos = client.publish(buildPublishRequest("test/topic") {
            payload("This is a test publish packet")
            desiredQoS = QoS.EXACTLY_ONE
            userProperties {
                "user" to "property"
            }
        })
        assertTrue(qos.isSuccess)
        assertEquals(QoS.EXACTLY_ONE, qos.getOrThrow())
    }

    @Test
    fun `subscribe to all`() = runTest {
        Logger.setLogWriters()
        client = MqttClient(host, 1884) {
            username = "ro"
            password = "readonly"
        }
        val connected = client.connect()
        assertTrue(connected.isSuccess)

        val subscribed = client.subscribe(listOf(TopicFilter(Topic("#"))))
        assertTrue(subscribed.isSuccess)

        var count = 0
        val packets = client.publishedPackets.takeWhile {
            count++
            count <= 50_000
        }.toList()
        val topics = packets.map { it.topic }.toSet()
        println("Packets: ${packets.size}, topics: ${topics.size}")
    }
}