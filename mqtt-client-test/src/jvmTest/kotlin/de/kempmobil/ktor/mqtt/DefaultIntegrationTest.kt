package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Publish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.decodeToString
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class DefaultIntegrationTest : IntegrationTestBase() {

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
        client.close()
    }

    @Test
    fun `connect returns NotAuthorized when using wrong credentials`() = runTest {
        client = createClient(pwd = "invalid-password")
        val connected = client.connect()

        assertTrue(connected.isSuccess)
        assertEquals(NotAuthorized, connected.getOrThrow().reason)
    }

    @Test
    fun `connect without credentials`() = runTest {
        client = createClient(user = null, pwd = null, port = mosquitto.defaultPortNoAuth)
        val connected = client.connect()

        assertTrue(connected.isSuccess)
    }

    @Test
    fun `connect with credentials`() = runTest {
        client = createClient()
        val connected = client.connect()

        assertTrue(connected.isSuccess)
    }

    @Test
    fun `allow reconnection after disconnect`() = runTest {
        client = createClient()
        val connected1 = client.connect()
        assertTrue(connected1.isSuccess)

        client.disconnect()
        Thread.sleep(10)
        val connected2 = client.connect()
        assertTrue(connected2.isSuccess)
    }

    @Test
    fun `send publish request with QoS 0`() = runTest {
        val id = "publisher-test-0"
        client = createClient(id = id)
        client.connect()

        val qos = client.publish(PublishRequest("test/topic/0", QoS.AT_MOST_ONCE))
        assertTrue(qos.isSuccess)

        client.disconnect()

        val logs = mosquitto.logs
        assertContains(logs, "Received PUBLISH from $id")
    }

    @Test
    fun `send publish request with QoS 1`() = runTest {
        val id = "publisher-test-1"
        client = createClient(id = id)
        client.connect()

        val qos = client.publish(PublishRequest("test/topic/1", QoS.AT_LEAST_ONCE))
        assertTrue(qos.isSuccess)

        client.disconnect()

        val logs = mosquitto.logs
        assertContains(logs, "Received PUBLISH from $id")
    }

    @Test
    fun `send publish request with QoS 2`() = runTest {
        val id = "publisher-test-2"
        client = createClient(id = id)
        client.connect()

        val qos = client.publish(PublishRequest("test/topic/2", QoS.EXACTLY_ONE))
        assertTrue(qos.isSuccess)

        client.disconnect()

        val logs = mosquitto.logs
        assertContains(logs, "Received PUBLISH from $id")
        assertContains(logs, "Received PUBREL from $id")
    }

    @Test
    fun `can subscribe to topics with different QoS values`() = runTest {
        client = createClient()
        client.connect()
        val result = client.subscribe(buildFilterList {
            add("topic/0", qoS = QoS.AT_MOST_ONCE)
            add("topic/1", qoS = QoS.AT_LEAST_ONCE)
            add("topic/2", qoS = QoS.EXACTLY_ONE)
        })

        assertTrue(result.isSuccess, "Cannot subscribe to topic: $result")
        assertEquals(listOf(GrantedQoS0, GrantedQoS1, GrantedQoS2), result.getOrThrow().reasons)
    }

    @Test
    fun `receive message with QoS 0`() = runTest {
        val topic = "test/topic/0"
        val id = "client-under-test"
        val payload = "text-payload-at-most-once"
        var receivedMessage: Publish? = null

        client = createClient(id = id)
        client.connect()
        val receiverJob = CoroutineScope(Dispatchers.Default).launch {
            receivedMessage = client.publishedPackets.first()
        }

        val suback = client.subscribe(buildFilterList {
            add(topic, qoS = QoS.AT_MOST_ONCE)
        })
        assertTrue(suback.isSuccess, "Cannot subscribe to '$topic': $suback")

        mosquitto.publish(topic, "0", payload)
        receiverJob.join()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
    }

    @Test
    fun `receive message with QoS 1`() = runTest {
        val topic = "test/topic/1"
        val id = "client-under-test"
        val payload = "text-payload-at-least-once"
        var receivedMessage: Publish? = null

        client = createClient(id = id)
        client.connect()
        val receiverJob = CoroutineScope(Dispatchers.Default).launch {
            receivedMessage = client.publishedPackets.first()
        }

        val suback = client.subscribe(buildFilterList {
            add(topic, qoS = QoS.AT_LEAST_ONCE)
        })
        assertTrue(suback.isSuccess, "Cannot subscribe to '$topic': $suback")

        mosquitto.publish(topic, "1", payload)
        receiverJob.join()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
        assertTrue(
            mosquitto.logs.contains("Received PUBACK from $id"),
            "Server should have received a PUBACK message"
        )
    }

    @Test
    fun `receive message with QoS 2`() = runTest {
        val topic = "test/topic/2"
        val id = "client-under-test"
        val payload = "text-payload-exactly-one"
        var receivedMessage: Publish? = null

        client = createClient(id = id)
        assertTrue(client.connect().isSuccess)
        val receiverJob = CoroutineScope(Dispatchers.Default).launch {
            receivedMessage = client.publishedPackets.first()
        }

        val suback = client.subscribe(buildFilterList {
            add(topic, qoS = QoS.EXACTLY_ONE)
        })
        assertTrue(suback.isSuccess, "Cannot subscribe to '$topic': $suback")

        mosquitto.publish(topic, "2", payload)
        receiverJob.join()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
        assertTrue(
            mosquitto.logs.contains("Received PUBCOMP from $id"),
            "Server should have received a PUBCOMP message"
        )
    }

    @Test
    fun `can receive 1000 messages with QoS 2`() = runTest(timeout = 2.seconds) {
        val messages = 1000

        val duration = measureTime {
            val topic = "test/performance/2"
            var count = 0

            client = createClient()
            assertTrue(client.connect().isSuccess)

            val counterJob = backgroundScope.launch {
                client.publishedPackets.takeWhile {
                    ++count < messages
                }.collect()
            }

            val suback = client.subscribe(buildFilterList {
                add(topic, qoS = QoS.EXACTLY_ONE)
            })
            assertTrue(suback.isSuccess, "Cannot subscribe to '$topic': $suback")

            mosquitto.publish(topic, "2", "payload", messages)
            counterJob.join()

            assertEquals(messages, count)
        }
        println("Collected $messages message in just $duration")
    }

    private fun createClient(
        user: String? = MosquittoContainer.user,
        pwd: String? = MosquittoContainer.password,
        id: String = "",
        port: Int = mosquitto.defaultPort
    ): MqttClient {
        return MqttClient(mosquitto.host, port) {
            username = user
            password = pwd
            clientId = id
        }
    }

    @Suppress("TestFunctionName")
    private fun PublishRequest(topic: String, qoS: QoS): PublishRequest {
        return buildPublishRequest(topic) {
            payload("This is a test publish packet")
            desiredQoS = qoS
            userProperties {
                "user" to "property"
            }
        }
    }
}