package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Severity
import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.Logger
import de.kempmobil.ktor.mqtt.util.toTopic
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class DefaultEngineTest {

    private val defaultHost = "localhost"
    private val defaultPort = 12345

    private var stopServerJob: Job? = null

    @AfterTest
    fun cleanup() {
        stopServer()
    }

    private fun stopServer() {
        stopServerJob?.run {
            stopServerJob = null
            runBlocking {
                withTimeout(2.seconds) {
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
        MqttEngine().use { engine ->
            val result = engine.start()

            assertTrue(result.isFailure)
            assertFalse(engine.connected.value)
        }
    }

    @Test
    fun `when the server is reachable return success`() = runTest {
        stopServerJob = startServer()
        MqttEngine().use { engine ->
            val result = engine.start()

            assertTrue(result.isSuccess)
            assertTrue(engine.connected.value)
        }
    }

    @Test
    fun `when terminating a connected session the connection state is updated`() = runTest {
        stopServerJob = startServer()
        MqttEngine().use { engine ->
            val result = engine.start()

            assertTrue(result.isSuccess)
            assertTrue(engine.connected.value)

            stopServerJob?.start()
            stopServerJob?.join()
            stopServerJob = null

            withContext(Dispatchers.Default) { // See runTest { } on why we need this
                withTimeout(1.seconds) {       // It takes a few millis until the connection is actually closed
                    engine.connected.first { isConnected -> !isConnected }
                }
            }
            // No need for an assertion here, as the test will fail with a TimeoutCancellationException when
            // not receiving the disconnection event.
        }
    }

    @Test
    fun `when disconnecting a connected session the connection state is updated`() = runTest {
        stopServerJob = startServer()
        MqttEngine().use { engine ->
            val result = engine.start()

            assertTrue(result.isSuccess)
            assertTrue(engine.connected.value)

            engine.disconnect()

            assertFalse(engine.connected.value)
        }
    }

    @Test
    fun `when reconnecting a failed connection the second attempt succeeds`() = runTest {
        MqttEngine().use { engine ->
            val failing = engine.start()  // This connection fails as the server is not started

            assertFalse(failing.isSuccess)
            assertFalse(engine.connected.value)

            stopServerJob = startServer()
            val result = engine.start()

            assertTrue(result.isSuccess)
            assertTrue(engine.connected.value)
        }
    }

    @Test
    fun `when sending a packet it is received by server`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>(replay = 1)
        stopServerJob = startServer(reader = {
            serverPackets.emit(readPacket())
        })

        val expected = Publish(topic = "test-topic".toTopic(), payload = "1234567890".encodeToByteString())
        MqttEngine().use { engine ->
            engine.start()
            engine.send(expected)

            val actual = serverPackets.first()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `when the server sends a packet the received packets are updated`() = runTest {
        val serverPackets = MutableSharedFlow<Packet>(replay = 1)
        stopServerJob = startServer(writer = {
            serverPackets.collect {
                write(it)
            }
        })

        val expected = Publish(topic = "test-topic".toTopic(), payload = "1234567890".encodeToByteString())
        MqttEngine().use { engine ->
            engine.start()
            serverPackets.emit(expected)

            val actual = engine.packetResults.first()
            assertEquals(expected, actual.getOrNull())
        }
    }

    @Test
    fun `when receiving a malformed packet return a MalformedPacketException`() = runTest {
        val dataToSend = MutableSharedFlow<ByteArray>(replay = 1)

        stopServerJob = startServer(writer = {
            dataToSend.collect {
                writeFully(it)
            }
        })

        MqttEngine().use { engine ->
            engine.start()
            dataToSend.emit(byteArrayOf(0, 0, 0))

            val result = engine.packetResults.first()
            assertTrue(result.isFailure)
            assertIs<MalformedPacketException>(result.exceptionOrNull())
        }
    }

    @Test
    fun `when calling send on a disconnected connection return a failure`() = runTest {
        stopServerJob = startServer()
        MqttEngine().use { engine ->
            engine.start()
            engine.disconnect()

            val result = engine.send(Pingreq)
            assertTrue(result.isFailure)
            assertIs<ConnectionException>(result.exceptionOrNull())
        }
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
        reader: (suspend ByteReadChannel.() -> Unit)? = null,
        writer: (suspend ByteWriteChannel.() -> Unit)? = null
    ): Job {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind(defaultHost, defaultPort)
        var socket: Socket? = null

        backgroundScope.launch {
            try {
                socket = serverSocket.accept().also { accepted ->
                    Logger.d { "Client connected successfully" }
                    if (reader != null) {
                        accepted.openReadChannel().reader()
                    }
                    if (writer != null) {
                        accepted.openWriteChannel(autoFlush = true).writer()
                    }
                }
            } catch (_: CancellationException) {
                // ignore
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

