package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@Ignore
class MosquittoTest {

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

    @Test
    fun `publishing a sample packet`() = runTest {
        client = MqttClient(mosquitto, 1883) { }
        val result = client.connect()
        assertTrue(result.isSuccess)

        val response = client.publish(PublishRequest("test/topic") {
            payload("This is a test publish packet")
            desiredQoS = QoS.EXACTLY_ONE
            userProperties {
                "user" to "property"
            }
        })
        assertTrue(response.isSuccess)
        assertIs<ExactlyOnePublishResponse>(response.getOrThrow())
        assertEquals(0, response.getOrThrow().reason.code)
    }

    @Test
    fun `subscribe to all`() = runTest {
        client = MqttClient(mosquitto, 1884) {
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