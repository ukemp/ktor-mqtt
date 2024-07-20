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

class MqttConnectionImplTest {

    private val defaultHost = "localhost"
    private val defaultPort = 12345

    @Test
    fun `the initial connection state is disconnected`() {
        val connection = MqttConnection()
        assertFalse(connection.connected.value)
    }

    @Test
    fun `when the server is not reachable return a failure`() = runTest {
        val connection = MqttConnection()
        val result = connection.start()

        assertTrue(result.isFailure)
        assertFalse(connection.connected.value)
    }

    @Test
    fun `when the server is reachable return success`() = runTest {
        val closeServer = startServer()
        val connection = MqttConnection()
        val result = connection.start()

        assertTrue(result.isSuccess)
        assertTrue(connection.connected.value)

        closeServer.start()
    }

    @Test
    fun `when terminating a connected session the connection state is updated`() = runTest {
        val closeServer = startServer()
        val connection = MqttConnection()
        val result = connection.start()

        assertTrue(result.isSuccess)
        assertTrue(connection.connected.value)

        closeServer.start()

        withContext(Dispatchers.Default) { // See runTest { } on why we need this
            withTimeout(1.seconds) {       // It takes a few millis until the connection is actually closed
                connection.connected.first { !it }
            }
        }
    }

    @Test
    fun `when disconnecting a connected session the connection state is updated`() = runTest {
        val closeServer = startServer()
        val connection = MqttConnection()
        val result = connection.start()

        assertTrue(result.isSuccess)
        assertTrue(connection.connected.value)

        connection.disconnect()

        assertFalse(connection.connected.first())

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

        val actual = connection.packetResults.first()
        assertEquals(expected, actual.getOrNull())

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

        val result = connection.packetResults.first()
        assertTrue(result.isFailure)
        assertIs<MalformedPacketException>(result.exceptionOrNull())

        closeServer.start()
    }

    @Test
    fun `when calling send on a disconnected connection return a failure`() = runTest {
        val closeServer = startServer()
        val connection = MqttConnection()
        connection.start()
        connection.disconnect()

        val result = connection.send(Pingreq)
        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())

        closeServer.start()
    }

    // ---- Helper functions -------------------------------------------------------------------------------------------

    @Suppress("TestFunctionName")
    private fun MqttConnection(host: String = defaultHost, port: Int = defaultPort): MqttConnectionImpl {
        return MqttConnectionImpl(MqttClientConfigBuilder(host, port).build())
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

