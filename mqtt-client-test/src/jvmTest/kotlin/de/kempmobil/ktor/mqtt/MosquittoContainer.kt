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
        .withExposedPorts(1883, 1884, 8883, 8080, 8443)

    val host: String
        get() = mosquitto.host

    /** Default Mosquitto port with `allow_anonymous false`. */
    val defaultPort: Int
        get() = mosquitto.getMappedPort(1884)

    /** Mosquitto port with `allow_anonymous true`. */
    val defaultPortNoAuth: Int
        get() = mosquitto.getMappedPort(1883)

    /** Mosquitto port requiring TLS (self-signed certificate) with `allow_anonymous false`. */
    val tlsPort: Int
        get() = mosquitto.getMappedPort(8883)

    /** Mosquitto websocket port with `allow_anonymous false`. */
    val wsPort: Int
        get() = mosquitto.getMappedPort(8080)

    /** Mosquitto websocket port requiring TLS (self-signed certificate) with `allow_anonymous false`. */
    val wssPort: Int
        get() = mosquitto.getMappedPort(8443)

    val logs: String
        get() = mosquitto.logs

    fun start() {
        try {
            mosquitto.start()
        } catch (throwable: Throwable) {
            println("Error creating Mosquitto image: $throwable")
            throwable.printStackTrace()
            throw throwable
        }

        println("Listing all exposed container ports:")
        println("------------------------------------")
        mosquitto.containerInfo.networkSettings.ports.bindings.forEach {
            println("Exposed port=${it.key}, bindings=${it.value.toList()}")
        }
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
     * @param repeats when greater 0, adds `--repeat ${repeats}` to the publish command and hence repeats the publish
     *        messages the specified number of times (without a delay)
     */
    fun publish(topic: String, qos: String, payload: String, repeats: Int = 0) {
        val commands = mutableListOf(
            "mosquitto_pub", "-h", "localhost", "-u", user, "-P", password, "-t", topic, "-q", qos, "-i",
            "test-publisher", "-m", payload
        )
        if (repeats > 0) {
            commands.add("--repeat")
            commands.add(repeats.toString())
        }
        val result = mosquitto.execInContainer(*(commands.toTypedArray()))

        if (result?.exitCode != 0) {
            System.err.println(result?.stderr)
        }
        assertEquals(0, result?.exitCode, "Exit code of '${commands.joinToString(separator = " ")}' should be zero")
    }

    companion object {
        const val user = "mqtt-test-user"
        const val password = "3n63hLKRV31fHf41NF95"  // Encrypted in the resources/passwd file!
    }
}