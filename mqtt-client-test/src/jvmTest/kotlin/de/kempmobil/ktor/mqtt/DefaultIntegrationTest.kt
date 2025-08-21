package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Severity
import de.kempmobil.ktor.mqtt.packet.Publish
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.io.bytestring.decodeToString
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class DefaultIntegrationTest : IntegrationTestBase() {

    private lateinit var client: MqttClient

    // -----------------------------------------------------------------------------------------------------------
    // IMPORTANT: Do not use runTest { } in the integration tests, use runBlocking { } instead to be able to check
    //            work in multithreading!
    // -----------------------------------------------------------------------------------------------------------

    @AfterTest
    fun tearDown() = runBlocking(Dispatchers.Default) {
        client.disconnect()
        client.close()
    }

    @Test
    fun `connect returns NotAuthorized when using wrong credentials`() = runBlocking(Dispatchers.Default) {
        client = createClient(pwd = "invalid-password")
        val connected = client.connect()

        assertTrue(connected.isSuccess)
        assertEquals(NotAuthorized, connected.getOrThrow().reason)
    }

    @Test
    fun `connect without credentials`() = runBlocking(Dispatchers.Default) {
        client = createClient(user = null, pwd = null, port = mosquitto.defaultPortNoAuth)
        val connected = client.connect()

        assertTrue(connected.isSuccess)
    }

    @Test
    fun `connect with credentials`() = runBlocking(Dispatchers.Default) {
        client = createClient()
        val connected = client.connect()

        assertTrue(connected.isSuccess)
    }

    @Test
    fun `connect via TLS with credentials`() = runBlocking(Dispatchers.Default) {
        client = createClient(port = mosquitto.tlsPort)
        val connected = client.connect()

        assertTrue(connected.isSuccess)
    }

    @Test
    fun `allow reconnection after disconnect`() = runBlocking(Dispatchers.Default) {
        client = createClient()
        val connected1 = client.connect()
        assertTrue(connected1.isSuccess)

        client.disconnect()
        Thread.sleep(10)
        val connected2 = client.connect()
        assertTrue(connected2.isSuccess)
    }

    @Test
    fun `allow reconnection after disconnect via TLS`() = runBlocking(Dispatchers.Default) {
        client = createClient(port = mosquitto.tlsPort)
        val connected1 = client.connect()
        assertTrue(connected1.isSuccess)

        client.disconnect()
        Thread.sleep(10)
        val connected2 = client.connect()
        assertTrue(connected2.isSuccess)
    }

    @Test
    fun `fail when connecting on non TLS port via TLS`() = runBlocking(Dispatchers.Default) {
        client = MqttClient(mosquitto.host, mosquitto.defaultPort) {
            connection {
                tls {
                    trustManager = NoTrustManager
                }
            }
        }

        val connected = client.connect()

        assertFalse(connected.isSuccess)
    }

    @Test
    fun `fail when connecting on TLS port without TLS configuration`() = runBlocking(Dispatchers.Default) {
        client = MqttClient(mosquitto.host, mosquitto.tlsPort) {
            ackMessageTimeout = 500.milliseconds
        }

        val connected = client.connect()

        assertFalse(connected.isSuccess)
    }

    @Test
    fun `send publish request with QoS 0`() = runBlocking(Dispatchers.Default) {
        val id = "publisher-test-0"
        client = createClient(id = id)
        client.connect()

        val qos = client.publish(simplePublishRequest("test/topic/0", QoS.AT_MOST_ONCE))
        assertTrue(qos.isSuccess)

        client.disconnect()

        val logs = mosquitto.logs
        assertContains(logs, "Received PUBLISH from $id")
    }

    @Test
    fun `send publish request with QoS 1`() = runBlocking(Dispatchers.Default) {
        val id = "publisher-test-1"
        client = createClient(id = id)
        client.connect()

        println("Before publishing")
        val qos = client.publish(simplePublishRequest("test/topic/1", QoS.AT_LEAST_ONCE))
        println("After publishing")
        assertTrue(qos.isSuccess)

        client.disconnect()

        println("Before logs")
        val logs = mosquitto.logs
        println("After logs")
        assertContains(logs, "Received PUBLISH from $id")
    }

    @Test
    fun `send publish request with QoS 2`() = runBlocking(Dispatchers.Default) {
        val id = "publisher-test-2"
        client = createClient(id = id)
        client.connect()

        val qos = client.publish(simplePublishRequest("test/topic/2", QoS.EXACTLY_ONE))
        assertTrue(qos.isSuccess)

        client.disconnect()

        val logs = mosquitto.logs
        assertContains(logs, "Received PUBLISH from $id")
        assertContains(logs, "Received PUBREL from $id")
    }

    @Test
    fun `can subscribe to topics with different QoS values`() = runBlocking(Dispatchers.Default) {
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
    fun `receive single message with QoS 0`() = runBlocking(Dispatchers.Default) {
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
    fun `receive single message with QoS 1`() = runBlocking(Dispatchers.Default) {
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
        delay(100.milliseconds)

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
        assertTrue(
            mosquitto.logs.contains("Received PUBACK from $id"),
            "Server should have received a PUBACK message"
        )
    }

    @Test
    fun `receive single message with QoS 2`() = runBlocking(Dispatchers.Default) {
        val topic = "test/topic/2"
        val id = "client-under-test"
        val payload = "text-payload-exactly-one"
        var receivedMessage: Publish? = null

        client = createClient(id = id)
        assertTrue(client.connect().isSuccess)
        val receiverJob = launch {
            receivedMessage = client.publishedPackets.first()
        }

        val suback = client.subscribe(buildFilterList {
            add(topic, qoS = QoS.EXACTLY_ONE)
        })
        assertTrue(suback.isSuccess, "Cannot subscribe to '$topic': $suback")

        mosquitto.publish(topic, "2", payload)
        receiverJob.join()
        delay(100.milliseconds)

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage.payload.decodeToString())
        assertTrue(
            mosquitto.logs.contains("Received PUBCOMP from $id"),
            "Server should have received a PUBCOMP message"
        )
    }

    @Test
    fun `receive 1000 messages with QoS 2`() = runBlocking(Dispatchers.Default) {
        val messages = 1000

        val duration = measureTime {
            val topic = "test/subscribe/qos2"
            var count = 0

            client = createClient(id = "qos2-subscriber")
            assertTrue(client.connect().isSuccess)

            val counterJob = launch {
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

    @Test
    fun `publish 1000 messages with QoS 1`() = runBlocking(Dispatchers.Default) {
        val messages = 1000
        client = createClient(id = "qos1-publisher")
        assertTrue(client.connect().isSuccess)
        val finishedCounter = AtomicInteger(0)

        coroutineScope {
            repeat(messages) { i ->
                launch {
                    val result = client.publish(PublishRequest("/test/publish/qos1") {
                        desiredQoS = QoS.AT_LEAST_ONCE
                        payload("payload-$i")
                    })

                    assertTrue(result.isSuccess, "Publish #$i failed: $result")
                    assertEquals(QoS.AT_LEAST_ONCE, result.getOrThrow().qoS)
                    finishedCounter.incrementAndGet()
                }
                if (i % 2 == 0) delay(1)  // Give some time to process handshake messages in between
            }
        }

        assertEquals(messages, finishedCounter.get(), "Not all publish operations completed.")
    }

    @Test
    fun `publish 1000 messages with QoS 2`() = runBlocking(Dispatchers.Default) {
        val messages = 1000
        client = createClient(id = "qos2-publisher")
        assertTrue(client.connect().isSuccess)
        val finishedCounter = AtomicInteger(0)

        coroutineScope {
            repeat(messages) { i ->
                launch {
                    val result = client.publish(PublishRequest("/test/publish/qos2") {
                        desiredQoS = QoS.EXACTLY_ONE
                        payload("payload-$i")
                    })

                    assertTrue(result.isSuccess, "Publish #$i failed: $result")
                    assertEquals(QoS.EXACTLY_ONE, result.getOrThrow().qoS)
                    finishedCounter.incrementAndGet()
                }
                if (i % 2 == 0) delay(1)  // Give some time to process handshake messages in between
            }
        }

        assertEquals(messages, finishedCounter.get(), "Not all publish operations completed.")
    }

    @Test
    fun `publish 1000 messages with any QoS`() = runBlocking(Dispatchers.Default) {
        val messages = 1000
        client = createClient(id = "qos-publisher")
        assertTrue(client.connect().isSuccess)
        val finishedCounter = AtomicInteger(0)

        coroutineScope {
            repeat(messages) { i ->
                launch {
                    val qoS = QoS.entries.toTypedArray().random()
                    val result = client.publish(PublishRequest("/test/publish/qos") {
                        desiredQoS = qoS
                        payload("payload-$i")
                    })

                    assertTrue(result.isSuccess, "Publish #$i failed: $result")
                    assertEquals(qoS, result.getOrThrow().qoS)
                    finishedCounter.incrementAndGet()
                }
                if (i % 2 == 0) delay(1)  // Give some time to process handshake messages in between
            }
        }

        assertEquals(messages, finishedCounter.get(), "Not all publish operations completed.")
    }

    private fun createClient(
        user: String? = MosquittoContainer.USER,
        pwd: String? = MosquittoContainer.PASSWORD,
        id: String = "",
        port: Int = mosquitto.defaultPort
    ): MqttClient {
        return MqttClient(mosquitto.host, port) {
            if (port == mosquitto.tlsPort) {
                connection {
                    tls {
                        trustManager = NoTrustManager
                    }
                }
            }
            username = user
            password = pwd
            clientId = id
            logging {
                minSeverity = Severity.Info
            }
        }
    }

    private fun simplePublishRequest(topic: String, qoS: QoS): PublishRequest {
        return PublishRequest(topic) {
            payload("This is a test publish packet")
            desiredQoS = qoS
            userProperties {
                "user" to "property"
            }
        }
    }
}