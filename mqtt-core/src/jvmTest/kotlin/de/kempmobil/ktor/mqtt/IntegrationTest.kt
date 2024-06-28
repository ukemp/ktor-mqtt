package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
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
        Logger.i(mosquitto.logs)
        mosquitto.stop()
    }

    @Test
    fun `connect to server`() = runTest {
        Logger.i { "Connecting to MQTT container $host:$port" }
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect(host, port = port)

        val connect = Connect(
            isCleanStart = true,
            willMessage = null,
            willOqS = QoS.AT_MOST_ONCE,
            retainWillMessage = false,
            keepAliveSeconds = 60u,
            clientId = "ktor-test",
            userName = user,
            password = password
        )

        val receiveChannel = socket.openReadChannel()
        val sendChannel = socket.openWriteChannel(autoFlush = true)
        val packetReceiver = object : PacketReceiver {
            override fun onConnect(connect: Connect) {
                Logger.i { "Received packet: $connect" }
            }

            override fun onConnack(connack: Connack) {
                Logger.i { "Received packet: $connack" }
                if (connack.reason == Success) {
                    val publish = Publish(
                        topicName = "test-topic",
                        payload = ByteString("testpayload".toByteArray())
                    )
                    Logger.i { "---------------" }
                    launch {
                        Logger.i { "Sending $publish..." }
                        sendChannel.write(publish)
                    }
                }
            }

            override fun onPublish(publish: Publish) {
                Logger.i { "Received packet: $publish" }
            }

            override fun onPuback(puback: Puback) {
                Logger.i { "Received packet: $puback" }
                socket.close()
                selectorManager.close()
            }

            override fun onPubrec(pubrec: Pubrec) {
                Logger.i { "Received packet: $pubrec" }
            }

            override fun onPubrel(pubrel: Pubrel) {
                Logger.i { "Received packet: $pubrel" }
            }

            override fun onPubcomp(pubcomp: Pubcomp) {
                Logger.i { "Received packet: $pubcomp" }
            }

            override fun onSubscribe(subscribe: Subscribe) {
                Logger.i { "Received packet: $subscribe" }
            }

            override fun onSuback(suback: Suback) {
                Logger.i { "Received packet: $suback" }
            }

            override fun onUnsubscribe(unsubscribe: Unsubscribe) {
                Logger.i { "Received packet: $unsubscribe" }
            }

            override fun onUnsuback(unsuback: Unsuback) {
                Logger.i { "Received packet: $unsuback" }
            }

            override fun onPingreq() {
                Logger.i { "Received packet: PINGREQ" }
            }

            override fun onPingresp() {
                Logger.i { "Received packet: PINGRESP" }
            }

            override fun onDisconnect(disconnect: Disconnect) {
                Logger.i { "Received packet: $disconnect" }
            }

            override fun onAuth(auth: Auth) {
                Logger.i { "Received packet: $auth" }
            }

            override fun onMalformedPacket(exception: MalformedPacketException) {
                Logger.e(throwable = exception) { "malformed packet received" }
            }

        }

        launch(Dispatchers.IO) {
            while (!socket.isClosed) {
                Logger.i { "Waiting for next packet..." }
                receiveChannel.readPacket(packetReceiver)
            }
            Logger.i { "Socket closed" }
        }

        launch(Dispatchers.IO) {
            Logger.i { "Sending connect request..." }
            sendChannel.write(connect)
        }
        Logger.i { "Terminating..." }
    }
}