package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Severity
import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.Logger
import de.kempmobil.ktor.mqtt.util.toTopic
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

class DefaultEngineTest {

    private val defaultHost = "127.0.0.1"
    private val defaultPort = 12345

    @Test
    fun `the initial connection state is disconnected`() {
        val engine = MqttEngine()
        assertFalse(engine.connected.value)
    }

    @Test
    fun `when the server is not reachable return a failure`() = runTest {
        val engine = MqttEngine()
        val result = engine.start()

        assertTrue(result.isFailure)
        assertFalse(engine.connected.value)
    }

    @Test
    fun `when the server is reachable return success`() = runTest {
        val closeServer = startServer(this)
        val engine = MqttEngine()
        val result = engine.start()

        assertTrue(result.isSuccess)
        assertTrue(engine.connected.value)

        closeServer.start()
    }

    @Test
    fun `when terminating a connected session the connection state is updated`() = runTest {
        val closeServer = startServer(this)
        val engine = MqttEngine()
        val result = engine.start()

        assertTrue(result.isSuccess)
        assertTrue(engine.connected.value)

        closeServer.start()

        withContext(Dispatchers.Default) { // See runTest { } on why we need this
            withTimeout(1.seconds) {       // It takes a few millis until the connection is actually closed
                engine.connected.first { isConnected -> !isConnected }
            }
        }
    }

    @Test
    fun `when disconnecting a connected session the connection state is updated`() = runTest {
        val closeServer = startServer(this)
        val engine = MqttEngine()
        val result = engine.start()

        assertTrue(result.isSuccess)
        assertTrue(engine.connected.value)

        engine.disconnect()

        assertFalse(engine.connected.first())

        closeServer.start() // Cleanup
    }

    @Test
    fun `when sending a packet it is received by server`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>()
        val closeServer = startServer(this, reader = {
            backgroundScope.launch {
                serverPackets.emit(readPacket())
            }
        })

        val expected = Publish(topic = "test-topic".toTopic(), payload = "1234567890".encodeToByteString())
        val engine = MqttEngine()
        engine.start()
        engine.send(expected)

        val actual = serverPackets.first()
        assertEquals(expected, actual)

        closeServer.start()
    }

    @Test
    fun `when the server sends a packet the received packets are updated`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>(replay = 1)
        val closeServer = startServer(this, writer = {
            backgroundScope.launch {
                serverPackets.collect {
                    write(it)
                }
            }
        })

        val expected = Publish(topic = "test-topic".toTopic(), payload = "1234567890".encodeToByteString())
        val engine = MqttEngine()
        engine.start()
        serverPackets.emit(expected)

        val actual = engine.packetResults.first()
        assertEquals(expected, actual.getOrNull())

        closeServer.start()
    }

    @Test
    fun `when receiving a malformed packet the connection is terminated with a disconnect packet`() = runTest {
        val dataToSend = MutableSharedFlow<ByteArray>(replay = 1)
        val receivedPackets = MutableSharedFlow<Packet>()

        val closeServer = startServer(this, writer = {
            CoroutineScope(Dispatchers.Default).launch {
                delay(100)
                dataToSend.collect {
                    writeFully(it)
                }
            }
        }, reader = {
            backgroundScope.launch {
                receivedPackets.emit(readPacket())
            }
        })

        val engine = MqttEngine()
        engine.start()
        dataToSend.emit(byteArrayOf(0, 0, 0))

        val result = engine.packetResults.first()
        assertTrue(result.isFailure)
        assertIs<MalformedPacketException>(result.exceptionOrNull())

        closeServer.start()
    }

    @Test
    fun `when calling send on a disconnected connection return a failure`() = runTest {
        val closeServer = startServer(this)
        val engine = MqttEngine()
        engine.start()
        engine.disconnect()

        val result = engine.send(Pingreq)
        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())

        closeServer.start()
    }

    // ---- Helper functions -------------------------------------------------------------------------------------------

    @Suppress("TestFunctionName")
    private fun MqttEngine(host: String = defaultHost, port: Int = defaultPort): MqttEngine {
        Logger.configureLogging {
            minSeverity = Severity.Verbose
        }
        return DefaultEngine(DefaultEngineConfig(host, port))
    }

    /**
     * Starts a socket server and returns an (unstarted) [Job] to stop it.
     */
    private suspend fun startServer(
        testScope: TestScope,
        reader: (ByteReadChannel.() -> Unit)? = null,
        writer: (ByteWriteChannel.() -> Unit)? = null
    ): Job {
        try {
            val selectorManager = SelectorManager(Dispatchers.Default)
            val serverSocket = aSocket(selectorManager).tcp().bind(defaultHost, defaultPort)

            val socketAcceptor = testScope.async {
                serverSocket.accept().also { socket ->
                    if (reader != null) {
                        socket.openReadChannel().reader()
                    }
                    if (writer != null) {
                        socket.openWriteChannel(autoFlush = true).writer()
                    }
                }
            }

            return testScope.launch(start = CoroutineStart.LAZY) {
                socketAcceptor.await().close()
                serverSocket.dispose()
                selectorManager.close()
                // Let the sockets close
                runBlocking {
                    delay(100)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw ex
        }
    }
}

