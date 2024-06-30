package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import kotlinx.coroutines.test.runTest
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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

    @Test
    fun `connect to server`() = runTest {
        Logger.i { "Connecting to MQTT container $host:$port" }
        val client = MqttClient(host, port) {
            userName = testUser
            password = testPassword
        }
        client.start()
        Thread.sleep(2000)
        client.stop()

        Logger.i { "Terminating..." }
    }
}