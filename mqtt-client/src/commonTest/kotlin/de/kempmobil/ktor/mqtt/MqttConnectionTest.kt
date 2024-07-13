package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.readPacket
import de.kempmobil.ktor.mqtt.packet.write
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MqttConnectionTest {

    private val defaultHost = "localhost"
    private val defaultPort = 12345

    private var closeServer: Job? = null

    @Test
    fun `the initial connection state is disconnected`() {
        val connection = MqttConnection()
        assertSame(Disconnected, connection.state.value)
    }

    @Test
    fun `when the server is not reachable return a failure`() = runTest {
        val connection = MqttConnection()
        val result = connection.start()

        assertTrue(result.isFailure)
        assertSame(Disconnected, connection.state.value)
    }

    @Test
    fun `when the server is reachable return success`() = runTest {
        val closeServer = startServer()
        val connection = MqttConnection()
        val result = connection.start()

        assertTrue(result.isSuccess)
        assertSame(Connected, connection.state.value)

        closeServer.start()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `when terminating a connected session the connection state is updated`() = runTest {
        val closeServer = startServer()
        val connection = MqttConnection()
        val result = connection.start()

        assertTrue(result.isSuccess)
        assertSame(Connected, connection.state.value)

        closeServer.start()

        // It takes a few milliseconds until the connection has been closed, hence we need to wait:
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.seconds) {
                connection.state.first { it is Disconnected }
            }
        }
    }

    @Test
    fun `when disconnecting a connected session the connection state is updated`() = runTest {
        val closeServer = startServer()
        val connection = MqttConnection()
        val result = connection.start()

        assertTrue(result.isSuccess)
        assertSame(Connected, connection.state.value)

        connection.disconnect()

        connection.state.first { it is Disconnected }

        closeServer.start() // Cleanup
    }

    @Test
    fun `when sending a packet it is received by server`() = runTest {
        val packet = Publish(topicName = "test-topic", payload = "1234567890".encodeToByteString())
        val serverPackets = MutableSharedFlow<Packet>()
        val closeServer = startServer(reader = {
            launch {
                serverPackets.emit(readPacket())
            }
        })
        val connection = MqttConnection()
        connection.start()

        connection.send(packet)
        serverPackets.first { it == packet } // Will time out when we don't receive the right packet

        closeServer.start()
    }

    @Test
    fun `when the server sends a packet the received packets are updated`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>(replay = 1)
        val closeServer = startServer(writer = {
            backgroundScope.launch {
                serverPackets.collect {
                    println("Writing $it")
                    write(it)
                    flush()
                }
            }
        })

        val expected = Publish(topicName = "test-topic", payload = "1234567890".encodeToByteString())
        val connection = MqttConnection()
        connection.start()

        serverPackets.emit(expected)
        val actual = connection.packetsReceived.first()

        assertEquals(expected, actual)
        closeServer.start()
    }

    // ---- Helper functions -------------------------------------------------------------------------------------------

    @Suppress("TestFunctionName")
    private fun MqttConnection(host: String = defaultHost, port: Int = defaultPort): MqttConnection {
        return MqttConnection(MqttClientConfigBuilder(host, port).build())
    }

    /**
     * Starts a socket server and returns an (unstarted) [Job] to stop it.
     */
    private fun TestScope.startServer(
        reader: (ByteReadChannel.() -> Unit)? = null,
        writer: (ByteWriteChannel.() -> Unit)? = null
    ): Job {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind(defaultHost, defaultPort)

        val waitForSocket = async {
            serverSocket.accept().also { socket ->
                if (reader != null) {
                    socket.openReadChannel().reader()
                }
                if (writer != null) {
                    socket.openWriteChannel().writer()
                }
            }
        }

        return launch(start = CoroutineStart.LAZY) {
            waitForSocket.await().close()
            serverSocket.dispose()
            selectorManager.close()
        }
    }
}

