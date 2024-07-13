package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class MqttConnectionTest {

    private val defaultHost = "localhost"
    private val defaultPort = 12345

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

        val state = connection.state.first()
        assertIs<Disconnected>(state)

        closeServer.start() // Cleanup
    }

    @Test
    fun `when sending a packet it is received by server`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>()
        val closeServer = startServer(reader = {
            backgroundScope.launch {
                serverPackets.emit(readPacket())
            }
        })

        val expected = Publish(topicName = "test-topic", payload = "1234567890".encodeToByteString())
        val connection = MqttConnection()
        connection.start()
        connection.send(expected)

        val actual = serverPackets.first()
        assertEquals(expected, actual)

        closeServer.start()
    }

    @Test
    fun `when the server sends a packet the received packets are updated`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>(replay = 1)
        val closeServer = startServer(writer = {
            backgroundScope.launch {
                serverPackets.collect {
                    write(it)
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

    @Test
    fun `when receiving a malformed packet the connection is terminated with a disconnect packet`() = runTest {
        val dataToSend = MutableSharedFlow<ByteArray>(replay = 1)
        val receivedPackets = MutableSharedFlow<Packet>()

        val closeServer = startServer(writer = {
            backgroundScope.launch {
                dataToSend.collect {
                    writeFully(it)
                }
            }
        }, reader = {
            backgroundScope.launch {
                receivedPackets.emit(readPacket())
            }
        })

        val connection = MqttConnection()
        connection.start()
        dataToSend.emit(byteArrayOf(0, 0, 0))

        val packet = receivedPackets.first()
        assertIs<Disconnect>(packet)

        val state = connection.state.first()
        assertIs<Disconnected>(state)

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

        val socketAcceptor = async {
            serverSocket.accept().also { socket ->
                if (reader != null) {
                    socket.openReadChannel().reader()
                }
                if (writer != null) {
                    socket.openWriteChannel(autoFlush = true).writer()
                }
            }
        }

        return launch(start = CoroutineStart.LAZY) {
            socketAcceptor.await().close()
            serverSocket.dispose()
            selectorManager.close()
        }
    }
}

