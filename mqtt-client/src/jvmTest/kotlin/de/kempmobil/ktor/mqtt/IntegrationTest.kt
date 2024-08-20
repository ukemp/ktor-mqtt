package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.Publish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.decodeToString
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class IntegrationTest {

    // To use podman instead of docker with testcontainers, run the following once on your system:
    // systemctl --user enable --now podman.socket
    //
    // Then create a file .testcontainers.properties in your home directory using these commands:
    //
    // echo docker.host=unix:///run/user/${UID}/podman/podman.sock > .testcontainers.properties
    // echo ryuk.container.image=docker.io/testcontainers/ryuk:lastest >> .testcontainers.properties
    //
    // Note: ${UID} must be replaced with the actual value in the .properties file, hence use the "echo"
    // command instead of pasting the properties directly into the file!!!


    private lateinit var host: String
    private var port: Int = -1
    private val testUser = "mqtt-test-user"
    private val testPassword = "3n63hLKRV31fHf41NF95"  // Encrypted in the resources/passwd file!
    private lateinit var client: MqttClient

    @Container
    var mosquitto: GenericContainer<*> = GenericContainer(
        ImageFromDockerfile()
            .withFileFromClasspath("mosquitto.conf", "mosquitto.conf")
            .withFileFromClasspath("passwd", "passwd")
            .withFileFromClasspath("Dockerfile", "Dockerfile")
    )
        .withExposedPorts(1883)


    @BeforeTest
    fun setup() {
        mosquitto.start()
        host = mosquitto.host
        port = mosquitto.firstMappedPort
    }

    @AfterTest
    fun tearDown() {
        Logger.i(mosquitto.logs)
        mosquitto.stop()
        client.close()
    }

    @Test
    fun `connect returns NotAuthorized when using wrong credentials`() = runTest {
        client = MqttClient(host, port) {
            userName = testUser
            password = "invalid-password"
        }
        val result = client.connect()

        assertTrue(result.isSuccess)
        assertEquals(NotAuthorized, result.getOrThrow().reason)
    }

    @Test
    fun `connection state propagated properly`() = runTest {
        client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }

        assertEquals(Disconnected, client.connectionState.first())
        val result = client.connect()

        assertEquals(Connected(result.getOrThrow()), client.connectionState.first())
        mosquitto.stop()

        assertEquals(Disconnected, client.connectionState.first())
    }

    @Test
    fun `allow reconnection after disconnect`() = runTest {
        client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
        val result1 = client.connect()
        assertNotNull(result1)
        assertTrue(result1.isSuccess)

        client.disconnect()
        val result2 = client.connect()
        println("Reconnect result: $result2")
        assertNotNull(result2)
        assertTrue(result2.isSuccess)

        client.disconnect()
    }

    @Test
    fun `send publish request`() = runTest(timeout = 4.seconds) {
        client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
        client.connect()

        val qos = client.publish(buildPublishRequest("test/topic") {
            payload("This is a test publish packet")
            desiredQoS = QoS.EXACTLY_ONE
            userProperties {
                "user" to "property"
            }
        })

        println("Published: $qos")
        client.disconnect()

        Logger.i { "Terminating..." }
    }

    @Test
    fun `can subscribe to topics with different QoS values`() = runTest {
        client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
        client.connect()
        val result = client.subscribe(buildFilterList {
            add("topic/0", qoS = QoS.AT_MOST_ONCE)
            add("topic/1", qoS = QoS.AT_LEAST_ONCE)
            add("topic/2", qoS = QoS.EXACTLY_ONE)
        })

        assertTrue(result.isSuccess, "Cannot subscribe to topic: $result")
        assertEquals(listOf(GrantedQoS0, GrantedQoS1, GrantedQoS2), result.getOrThrow().reasons)

        client.disconnect()
    }

    @Test
    fun `receive message with QoS AT_MOST_ONCE`() = runTest {
        val topic = "test/topic/0"
        val id = "client-under-test"
        val payload = "text-payload-at-most-once"
        var receivedMessage: Publish? = null

        client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
            clientId = id
        }
        client.connect()
        val receiverJob = CoroutineScope(Dispatchers.Default).launch {
            receivedMessage = client.publishedPackets.first()
        }

        val suback = client.subscribe(buildFilterList {
            add(topic, qoS = QoS.AT_MOST_ONCE)
        })
        assertTrue(suback.isSuccess, "Cannot subscribe to '$topic': $suback")

        sendMessage(topic, "0", payload)
        Thread.sleep(200)
        receiverJob.cancel()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())

        client.disconnect()
    }

    @Test
    fun `receive message with QoS AT_LEAST_ONCE`() = runTest {
        val topic = "test/topic/1"
        val id = "client-under-test"
        val payload = "text-payload-at-least-once"
        var receivedMessage: Publish? = null

        client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
            clientId = id
        }
        client.connect()
        val receiverJob = CoroutineScope(Dispatchers.Default).launch {
            receivedMessage = client.publishedPackets.first()
        }

        val suback = client.subscribe(buildFilterList {
            add(topic, qoS = QoS.AT_LEAST_ONCE)
        })
        assertTrue(suback.isSuccess, "Cannot subscribe to '$topic': $suback")

        sendMessage(topic, "1", payload)
        Thread.sleep(200)
        receiverJob.cancel()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
        assertTrue(
            mosquitto.logs.contains("Received PUBACK from $id"),
            "Server should have received a PUBACK message"
        )

        client.disconnect()
    }

    @Test
    fun `receive message with QoS EXACTLY_ONE`() = runTest {
        val topic = "test/topic/2"
        val id = "client-under-test"
        val payload = "text-payload-exactly-one"
        var receivedMessage: Publish? = null

        client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
            clientId = id
        }
        client.connect()
        val receiverJob = CoroutineScope(Dispatchers.Default).launch {
            receivedMessage = client.publishedPackets.first()
        }

        val suback = client.subscribe(buildFilterList {
            add(topic, qoS = QoS.EXACTLY_ONE)
        })
        assertTrue(suback.isSuccess, "Cannot subscribe to '$topic': $suback")

        sendMessage(topic, "2", payload)
        Thread.sleep(200)
        receiverJob.cancel()

        assertNotNull(receivedMessage)
        assertEquals(payload, receivedMessage!!.payload.decodeToString())
        assertTrue(
            mosquitto.logs.contains("Received PUBCOMP from $id"),
            "Server should have received a PUBCOMP message"
        )

        client.disconnect()
    }

    private fun sendMessage(topic: String, qos: String, payload: String) {
        // Use "mosquitto_pub" to send a message to our client:
        val result = mosquitto.execInContainer(
            "mosquitto_pub", "-h", "localhost", "-u", testUser, "-P", testPassword, "-t", topic, "-q", qos,
            "-i", "test-publisher", "-m", payload
        )

        assertEquals(0, result.exitCode, "Exit code of 'mosquitto_pub' should be zero")
    }
}