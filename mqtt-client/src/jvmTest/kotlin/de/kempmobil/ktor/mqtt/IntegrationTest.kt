package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.Publish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.encodeToByteString
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class IntegrationTest {

    // To use podman with testcontainers, run the following:
    // systemctl --user enable --now podman.socket
    //
    // Then add the following environment variables (either in the run configuration of Intellij or as exported shell
    // variables):
    //
    // REPLACE ${UID} WITH THE ACTUAL VALUE, WHEN USING IT IN THE INTELLIJ RUN CONFIGURATION!
    //
    // DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
    // TESTCONTAINERS_RYUK_DISABLED=true

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
    ).withExposedPorts(1883)


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

//    @Test
//    fun `connection state propagated properly`() = runTest {
//        val client = MqttClient(host, port) {
//            userName = testUser
//            password = testPassword
//        }
//        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
//        client.connect()
//        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
//        mosquitto.stop()
//        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
//    }

    @Test
    fun `connect to server`() = runTest(timeout = 4.seconds) {
        Logger.i { "Connecting to MQTT container $host:$port" }
        val client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
        withContext(Dispatchers.Default.limitedParallelism(1)) {
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
}