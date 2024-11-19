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

@Ignore
class DefaultEngineTest {

    private val defaultHost = "localhost"
    private val defaultPort = 12345

    private var cleanupJob: Job? = null

    @AfterTest
    fun cleanup() {
        cleanupJob?.run {
            cleanupJob = null
            runBlocking {
                withTimeout(1.seconds) {
                    start()
                    join()
                }
            }
        }
    }

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
        cleanupJob = startServer()
        val engine = MqttEngine()
        val result = engine.start()

        assertTrue(result.isSuccess)
        assertTrue(engine.connected.value)
    }

    @Test
    fun `when terminating a connected session the connection state is updated`() = runTest {
        cleanupJob = startServer()
        val engine = MqttEngine()
        val result = engine.start()

        assertTrue(result.isSuccess)
        assertTrue(engine.connected.value)

        cleanupJob?.start()
        cleanupJob?.join()
        cleanupJob = null

        withContext(Dispatchers.Default) { // See runTest { } on why we need this
            withTimeout(1.seconds) {       // It takes a few millis until the connection is actually closed
                engine.connected.first { isConnected -> !isConnected }
            }
        }
    }

    @Test
    fun `when disconnecting a connected session the connection state is updated`() = runTest {
        cleanupJob = startServer()
        val engine = MqttEngine()
        val result = engine.start()

        assertTrue(result.isSuccess)
        assertTrue(engine.connected.value)

        engine.disconnect()

        assertFalse(engine.connected.first())
    }

    @Test
    fun `when sending a packet it is received by server`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>()
        cleanupJob = startServer(reader = {
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
    }

    @Test
    fun `when the server sends a packet the received packets are updated`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>(replay = 1)
        cleanupJob = startServer(writer = {
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
    }

    @Test
    fun `when receiving a malformed packet return a MalformedPacketException`() = runTest {
        val dataToSend = MutableSharedFlow<ByteArray>(replay = 1)

        cleanupJob = startServer(writer = {
            CoroutineScope(Dispatchers.Default).launch {
                delay(100)
                dataToSend.collect {
                    writeFully(it)
                }
            }
        })

        val engine = MqttEngine()
        engine.start()
        dataToSend.emit(byteArrayOf(0, 0, 0))

        val result = engine.packetResults.first()
        assertTrue(result.isFailure)
        assertIs<MalformedPacketException>(result.exceptionOrNull())
    }

    @Test
    fun `when calling send on a disconnected connection return a failure`() = runTest {
        cleanupJob = startServer()
        val engine = MqttEngine()
        engine.start()
        engine.disconnect()

        val result = engine.send(Pingreq)
        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())
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
    private suspend fun TestScope.startServer(
        reader: (ByteReadChannel.() -> Unit)? = null,
        writer: (ByteWriteChannel.() -> Unit)? = null
    ): Job {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind(defaultHost, defaultPort)
        var socket: Socket? = null

        backgroundScope.launch {
            try {
                socket = serverSocket.accept().also { accepted ->
                    if (reader != null) {
                        accepted.openReadChannel().reader()
                    }
                    if (writer != null) {
                        accepted.openWriteChannel(autoFlush = true).writer()
                    }
                }
            } catch (ex: Exception) {
                fail("Cannot create server socket [$defaultHost:$defaultPort]", ex)
            }
        }

        // Don't use TestScope here, as this might get canceled after test execution!
        return CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) {
            socket?.close()
            serverSocket.close()
            selectorManager.close()
        }
    }
}

