package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
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
    fun `connect returns failure when server is not reachable`() = runTest(timeout = 1000.seconds) {
        mosquitto.stop()
        client = MqttClient(host, port) {
            ackMessageTimeout = 100.milliseconds
        }
        val result = client.connect()

        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())

        client.disconnect()
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

        val puback = client.publish(buildPublishRequest("test/topic") {
            payload("This is a test publish packet")
            desiredQoS = QoS.EXACTLY_ONE
            userProperties {
                "user" to "property"
            }
        })

        println("Published: $puback")
        client.disconnect()

        Logger.i { "Terminating..." }
    }

    @Test
    fun `subscribe to topic`() = runTest {
        client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
        client.connect()
        val suback = client.subscribe(buildFilterList {
            +"test/topic"
            add("another/topic", qoS = QoS.EXACTLY_ONE)
        })
        println("---------------> $suback")
        client.disconnect()
    }
}