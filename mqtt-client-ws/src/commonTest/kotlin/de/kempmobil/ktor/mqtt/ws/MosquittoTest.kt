package de.kempmobil.ktor.mqtt.ws

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.Topic
import de.kempmobil.ktor.mqtt.TopicFilter
import io.ktor.http.*
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
    fun `test unencrypted websocket connection`() = runTest {
        client = MqttClient(Url("http://$mosquitto:8080")) { }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `test encrypted websocket connection`() = runTest {
        client = MqttClient(Url("https://$mosquitto:8081")) { }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `subscribe to all`() = runTest {
        client = MqttClient(Url("http://$mosquitto:8090")) {
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