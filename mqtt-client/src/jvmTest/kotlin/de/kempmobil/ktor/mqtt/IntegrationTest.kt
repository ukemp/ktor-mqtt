package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.Publish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.decodeToString
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.*

class IntegrationTest {

    private lateinit var client: MqttClient

    companion object {

        lateinit var mosquitto: MosquittoContainer

        @JvmStatic
        @BeforeClass
        fun startServer() {
            mosquitto = MosquittoContainer().also { it.start() }
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            Logger.i(mosquitto.logs)
            mosquitto.stop()
        }
    }

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
        client.close()
    }

    @Test
    fun `connect returns NotAuthorized when using wrong credentials`() = runTest {
        client = createClient(pwd = "invalid-password")
        val result = client.connect()

        assertTrue(result.isSuccess)
        assertEquals(NotAuthorized, result.getOrThrow().reason)
    }

    @Test
    fun `allow reconnection after disconnect`() = runTest {
        client = createClient()
        val result1 = client.connect()
        assertNotNull(result1)
        assertTrue(result1.isSuccess)

        client.disconnect()
        val result2 = client.connect()
        assertNotNull(result2)
        assertTrue(result2.isSuccess)
    }

    @Test
    fun `send publish request`() = runTest {
        val id = "publisher-test"
        client = createClient(id = id)
        client.connect()

        val qos = client.publish(buildPublishRequest("test/topic") {
            payload("This is a test publish packet")
            desiredQoS = QoS.EXACTLY_ONE
            userProperties {
                "user" to "property"
            }
        })
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
    fun `receive message with QoS AT_MOST_ONCE`() = runTest {
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
        Thread.sleep(200)
        receiverJob.cancel()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
    }

    @Test
    fun `receive message with QoS AT_LEAST_ONCE`() = runTest {
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
        Thread.sleep(200)
        receiverJob.cancel()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
        assertTrue(
            mosquitto.logs.contains("Received PUBACK from $id"),
            "Server should have received a PUBACK message"
        )
    }

    @Test
    fun `receive message with QoS EXACTLY_ONE`() = runTest {
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
        Thread.sleep(200)
        receiverJob.cancel()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
        assertTrue(
            mosquitto.logs.contains("Received PUBCOMP from $id"),
            "Server should have received a PUBCOMP message"
        )
    }

//    @Test
//    fun `connection via TLS`() = runTest {
//        client = MqttClient {
//            connectTo(mosquitto.host, mosquitto.tlsPort) {
//                tls {
//                    // Don't check certificates:
//                    trustManager = object : X509TrustManager {
//                        override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOf()
//                        override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
//                        override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
//                    }
//                }
//            }
//            username = MosquittoContainer.user
//            password = MosquittoContainer.password
//        }
//
//        assertEquals(Disconnected, client.connectionState.first())
//        val result = client.connect()
//
//        assertEquals(Connected(result.getOrThrow()), client.connectionState.first())
//    }

    private fun createClient(
        user: String = MosquittoContainer.user,
        pwd: String = MosquittoContainer.password,
        id: String = ""
    ): MqttClient {
        return MqttClient {
            connectTo(mosquitto.host, mosquitto.defaultPort) { }
            username = user
            password = pwd
            clientId = id
        }
    }
}