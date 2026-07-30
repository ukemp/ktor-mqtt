package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Severity
import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.Logger
import de.kempmobil.ktor.mqtt.util.toTopic
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.mock
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.encodeToByteString
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DefaultEngineTest {

    companion object {
        private const val host = "localhost"
        private var port = 12345
    }

    private var stopServerJob: Job? = null

    @AfterTest
    fun cleanup() {
        stopServer()

        // Use a new port for the next test in case the previous socket is not closed quickly enough
        port++
    }

    private fun stopServer() {
        stopServerJob?.run {
            stopServerJob = null
            runBlocking {
                withTimeout(30.seconds) {
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
                withTimeout(5.seconds) {       // It takes a few millis until the connection is actually closed
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
    fun `a reconnect survives the late teardown of the previous connection`() = runTest(timeout = 60.seconds) {
        // Tearing down a connection is asynchronous: the receiver loop notices its cancellation and
        // cleans up on the engine's dispatcher. A reconnect that has already replaced the engine's
        // socket, send channel and receiver job by then must not be torn down by that late cleanup.
        // A single reconnect only fails when the cleanup loses the race, so cycle often enough that
        // losing at least once is practically certain. Each accepted connection greets the client
        // with a packet naming its cycle; when the receiver of a fresh connection was torn down, the
        // greeting never surfaces in packetResults.
        val cycles = 20
        val greeting = { cycle: Int ->
            Publish(topic = "test-topic".toTopic(), payload = "cycle-$cycle".encodeToByteString())
        }
        val acceptCount = AtomicInteger(0)
        stopServerJob = startServer(accepts = cycles, writer = {
            write(greeting(acceptCount.getAndIncrement()))
        })

        MqttEngine().use { engine ->
            withContext(Dispatchers.Default) { // See runTest { } on why we need this
                repeat(cycles) { cycle ->
                    assertTrue(engine.start().isSuccess, "Reconnect $cycle failed to establish a connection")

                    val expected = greeting(cycle)
                    withTimeout(5.seconds) {
                        engine.packetResults.first { it.getOrNull() == expected }
                    }

                    assertTrue(engine.connected.value, "Reconnect $cycle was torn down by the previous connection's cleanup")
                    engine.disconnect()
                }
            }
        }
    }

    @Test
    fun `when sending a packet it is received by server`() = runTest {
        val serverPackets = Channel<Packet>()
        stopServerJob = startServer(reader = {
            serverPackets.send(readPacket())
        })

        val expected = Publish(topic = "test-topic".toTopic(), payload = "1234567890".encodeToByteString())
        MqttEngine().use { engine ->
            engine.start()
            engine.send(expected)

            val actual = serverPackets.receive()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `when the server sends a packet the received packets are updated`() = runTest {
        val serverPackets = Channel<Packet>()
        stopServerJob = startServer(writer = {
            write(serverPackets.receive())
        })

        val expected = Publish(topic = "test-topic".toTopic(), payload = "1234567890".encodeToByteString())
        MqttEngine().use { engine ->
            engine.start()
            serverPackets.send(expected)

            val actual = engine.packetResults.first()
            assertEquals(expected, actual.getOrNull())
        }
    }

    @Test
    fun `when receiving a malformed packet return a MalformedPacketException`() = runTest {
        val dataToSend = Channel<ByteArray>()

        stopServerJob = startServer(writer = {
            writeFully(dataToSend.receive())
        })

        MqttEngine().use { engine ->
            engine.start()
            dataToSend.send(byteArrayOf(0, 0, 0))

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

    @Test
    fun `ensure connection times out after connection timeout`() = runTest(timeout = 2.seconds) {
        val config = DefaultEngineConfig("localhost", 1234).apply {
            connectionTimeout = 100.milliseconds
        }
        val socketHandler = mock<SocketHandler> {
            everySuspend { openSocket(config) } calls {
                // Block this coroutine to trigger a connectionTimeout:
                suspendCancellableCoroutine { }
            }
        }
        val engine = DefaultEngine(config, socketHandler)
        val connected = engine.start()

        assertFalse(connected.isSuccess, "Connection should not be successful")
        assertIs<ConnectionException>(connected.exceptionOrNull())
    }

    // ---- Helper functions -------------------------------------------------------------------------------------------

    private fun MqttEngine(): MqttEngine {
        Logger.configureLogging {
            minSeverity = Severity.Verbose
        }
        return DefaultEngine(DefaultEngineConfig(host, port), replay = 16)
    }

    /**
     * Starts a socket server accepting [accepts] consecutive connections and returns an (unstarted)
     * [Job] to stop it. The [reader] and [writer] callbacks are invoked once per accepted connection.
     */
    private suspend fun TestScope.startServer(
        accepts: Int = 1,
        reader: (suspend ByteReadChannel.() -> Unit)? = null,
        writer: (suspend ByteWriteChannel.() -> Unit)? = null
    ): Job {
        val selectorManager = SelectorManager(Dispatchers.Default)
        val serverSocket = aSocket(selectorManager).tcp().bind(host, port)
        val sockets = mutableListOf<Socket>()

        backgroundScope.launch {
            try {
                repeat(accepts) {
                    val accepted = serverSocket.accept()
                    sockets.add(accepted)
                    Logger.d { "Client connected successfully to $host:$port" }
                    if (reader != null) {
                        launch { runCatching { accepted.openReadChannel().reader() } }
                    }
                    if (writer != null) {
                        launch { runCatching { accepted.openWriteChannel(autoFlush = true).writer() } }
                    }
                }
            } catch (_: CancellationException) {
                // ignore
            } catch (_: ClosedChannelException) {
                // ignore, might be thrown by closing the socket
            } catch (ex: Exception) {
                fail("Cannot create server socket [$host:$port]", ex)
            }
        }

        // Don't use TestScope here, as this might get canceled after test execution!
        return CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) {
            sockets.forEach { it.close() }
            serverSocket.close()
            selectorManager.close()
        }
    }
}

