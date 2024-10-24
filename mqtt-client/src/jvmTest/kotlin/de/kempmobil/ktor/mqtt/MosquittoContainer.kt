package de.kempmobil.ktor.mqtt

import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import kotlin.test.assertEquals

class MosquittoContainer {

    // To use podman instead of docker with testcontainers, run the following once on your system:
    // systemctl --user enable --now podman.socket
    //
    // Then create a file .testcontainers.properties in your home directory using these commands:
    //
    // echo docker.host=unix:///run/user/${UID}/podman/podman.sock > .testcontainers.properties
    // echo ryuk.container.image=docker.io/testcontainers/ryuk:lastest >> .testcontainers.properties
    //
    // Note: ${UID} must be replaced with the actual value in the .properties file, hence use the "echo"
    //       command instead of pasting the properties directly into the file!

    private var mosquitto: GenericContainer<*> = GenericContainer(
        ImageFromDockerfile()
            .withFileFromClasspath("mosquitto.conf", "mosquitto.conf")
            .withFileFromClasspath("passwd", "passwd")
            .withFileFromClasspath("Dockerfile", "Dockerfile")
            .withFileFromClasspath("ca.crt", "ca.crt")
            .withFileFromClasspath("server.key", "server.key")
            .withFileFromClasspath("server.crt", "server.crt")
    )
        .withExposedPorts(1883, 8883, 8080, 8443)

    val host: String
        get() = mosquitto.host

    val defaultPort: Int
        get() = mosquitto.getMappedPort(1883)

    val tlsPort: Int
        get() = mosquitto.getMappedPort(8883)

    val wsPort: Int
        get() = mosquitto.getMappedPort(8080)

    val wssPort: Int
        get() = mosquitto.getMappedPort(8443)

    val logs: String
        get() = mosquitto.logs

    fun start() {
        mosquitto.start()
    }

    fun stop() {
        mosquitto.stop()
    }

    /**
     * Publishes the specified payload under the specified topic, using the specified QoS. This uses the command line
     * tool `mosquitto_pub` inside the container to send the publishing message to the server.
     *
     * @param topic the name of the topic to publish to
     * @param qos the QoS, either "0", "1" or "2"
     * @param payload the payload to publish
     */
    fun publish(topic: String, qos: String, payload: String) {
        val commands = arrayOf(
            "mosquitto_pub", "-h", "localhost", "-u", user, "-P", password, "-t", topic, "-q", qos, "-i",
            "test-publisher", "-m", payload
        )
        val result = mosquitto.execInContainer(*commands)

        assertEquals(0, result?.exitCode, "Exit code of '${commands.joinToString(separator = " ")}' should be zero")
    }

    companion object {
        const val user = "mqtt-test-user"
        const val password = "3n63hLKRV31fHf41NF95"  // Encrypted in the resources/passwd file!
    }
}