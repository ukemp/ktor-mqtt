package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.readVariableByteInt
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.text.toByteArray


@Testcontainers
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
    private val user = "mqtt-test-user"
    private val password = "3n63hLKRV31fHf41NF95"  // Encrypted in the resources/passwd file

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
        println(mosquitto.logs)
        mosquitto.stop()
    }

    @Test
    fun `connect to server`() = runTest {
        println(">>> Connecting to MQTT container $host:$port")
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect(host, port = port)

        val connect = Connect(
            isCleanStart = true,
            willMessage = null,
            willOqS = QoS.AT_LEAST_ONCE,
            retainWillMessage = false,
            keepAliveSeconds = 60u,
            clientId = "ktor-test",
            userName = user,
            password = password
        )

        val receiveChannel = socket.openReadChannel()
        val sendChannel = socket.openWriteChannel(autoFlush = true)

        launch(Dispatchers.IO) {
            val header = receiveChannel.readByte()
            val length = receiveChannel.readVariableByteInt()
            val packet = receiveChannel.readPacket(length)
            val connack = packet.readConnack()
            println(">>> Received packet: $connack")

            if (connack.reason == Success) {
                val publish = Publish(
                    topicName = "test-topic",
                    payload = ByteString("testpayload".toByteArray())
                )
                val pubPacket = buildPacket {
                    write(publish)
                }
                sendChannel.writeFixedHeader(publish, pubPacket.remaining.toInt())
                sendChannel.writePacket(pubPacket)
            }
            socket.close()
            selectorManager.close()
        }

        val packet = buildPacket {
            write(connect)
        }
        sendChannel.writeFixedHeader(connect, packet.remaining.toInt())
        sendChannel.writePacket(packet)
    }
}