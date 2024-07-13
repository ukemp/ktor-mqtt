package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.Publish
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.encodeToByteString
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
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
    private val testPassword = "3n63hLKRV31fHf41NF95"  // Encrypted in the resources/passwd file

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
    }

    @Test
    fun `connect returns proper values if server does not respond`() = runTest {
        val client = MqttClient(host, port) {
            userName = testUser
            password = "invalid-password"
        }
        var result = client.connect()
        println("---------------> $result")

        mosquitto.stop()
        result = client.connect()
        println("---------------> $result")
    }

    @Test
    fun `connection state propagated properly`() = runTest {
        val client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
//        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        client.connect()
//        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
        mosquitto.stop()
//        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun `allow reconnection after disconnect`() = runTest {
        val client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
        client.connect()
        client.disconnect()
        val connack = client.connect()
        assertNotNull(connack)
//        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)

        client.disconnect()
        client.close()
//        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun `connect to server`() = runTest(timeout = 4.seconds) {
        Logger.i { "Connecting to MQTT container $host:$port" }
        val client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
        val connack = client.connect()
        println("Received: $connack")
        val puback = client.publish(
            Publish(
                packetIdentifier = 42u,
                topicName = "test/topic",
                payload = "abc".encodeToByteString(),
                qoS = QoS.EXACTLY_ONE
            )
        )
        println("Published: $puback")
        client.disconnect()

        Logger.i { "Terminating..." }
    }
}