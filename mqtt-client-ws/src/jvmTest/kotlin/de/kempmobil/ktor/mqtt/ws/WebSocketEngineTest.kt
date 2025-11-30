package de.kempmobil.ktor.mqtt.ws

import co.touchlab.kermit.Severity
import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.Logger
import de.kempmobil.ktor.mqtt.util.toReasonString
import de.kempmobil.ktor.mqtt.util.toTopic
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class WebSocketEngineTest {

    companion object {
        private const val host = "localhost"
        private var port = 8088
    }
    private val limitedFrameSize = 10L

    private val samplePackets = listOf<Packet>(
        Publish(topic = "test-topic-1".toTopic(), payload = "1234567890".encodeToByteString()),
        Pingreq,
        Suback(
            packetIdentifier = 1u,
            reasons = listOf(GrantedQoS2, GrantedQoS1, GrantedQoS0),
            reasonString = "0123456789-ABCDEFGHIJKLNOMPRSTUVWXYZ".toReasonString()
        ),
        Pingresp,
        Disconnect(NormalDisconnection)
    )

    private var cleanupJob: Job? = null

    @AfterTest
    fun cleanup() {
        cleanupJob?.run {
            cleanupJob = null
            runBlocking {
                withTimeout(30.seconds) {
                    start()
                    join()
                }
            }
        }
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
        MqttEngine().use { engine ->
            val result = engine.start()

            assertTrue(result.isSuccess)
            assertTrue(engine.connected.value)
        }
    }

    @Test
    fun `when terminating a connected session the connection state is updated`() = runTest {
        cleanupJob = startServer()
        MqttEngine().use { engine ->
            val result = engine.start()

            assertTrue(result.isSuccess)
            assertTrue(engine.connected.value)

            cleanupJob?.start()
            cleanupJob?.join()
            cleanupJob = null

            withContext(Dispatchers.Default) { // See runTest { } on why we need this
                engine.connected.first { isConnected -> !isConnected }
            }
        }
    }

    @Test
    fun `when disconnecting a connected session the connection state is updated`() = runTest {
        cleanupJob = startServer()
        MqttEngine().use { engine ->
            assertFalse(engine.connected.value, "Engine should not be connected before start")

            val result = engine.start()
            assertTrue(result.isSuccess)
            assertTrue(engine.connected.value, "Engine should be connected after start")

            engine.disconnect()

            // withTimeout not surrounded by withContext will only wait for virtual time, i.e. no time
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(3.seconds) {
                    engine.connected.first { !it }
                }
            }

            assertFalse(engine.connected.value, "Engine should be disconnected after disconnect()")
        }
    }

    @Test
    fun `when the engine sends packets they are received by server`() = runTest {
        val receivedPackets = MutableSharedFlow<Packet>(replay = 30)
        cleanupJob = startServer(session = receiverSession(receivedPackets))

        MqttEngine().use { engine ->
            engine.start()
            samplePackets.forEach { engine.send(it) }

            val received = mutableListOf<Packet>()
            receivedPackets.take(samplePackets.size).toList(received)
            assertEquals(samplePackets, received)
        }
    }

    @Test
    fun `when the server sends packets they are received by the engine`() = runTest {
        val packetsToSend = Channel<List<Packet>>()

        cleanupJob = startServer(session = senderSession(packetsToSend))
        MqttEngine().use { engine ->
            engine.start()
            packetsToSend.send(samplePackets)

            val received = mutableListOf<Packet>()
            engine.packetResults.take(samplePackets.size).map { it.getOrThrow() }.toList(received)
            assertEquals(samplePackets, received)

            packetsToSend.send(emptyList()) // Signal to close the server connection
        }
    }

    @Test
    fun `when the server sends packets in more than one frame they are received by the client`() = runTest {
        val packetsToSend = Channel<List<Packet>>()

        cleanupJob = startServer(
            session = senderSessionWithLimitedFrameSize(packetsToSend),
            frameSize = limitedFrameSize
        )
        MqttEngine().use { engine ->
            engine.start()
            val received = mutableListOf<Packet>()

            val collectJob = launch {
                engine.packetResults.take(samplePackets.size).map { it.getOrThrow() }.toList(received)
            }
            packetsToSend.send(samplePackets)

            collectJob.join()
            packetsToSend.send(emptyList()) // Signal to close the server connection

            assertEquals(samplePackets, received)
        }
    }

    // ---- Helper functions -------------------------------------------------------------------------------------------

    @Suppress("TestFunctionName")
    private fun MqttEngine(): MqttEngine {
        Logger.configureLogging {
            minSeverity = Severity.Verbose
        }
        return WebSocketEngine(WebSocketEngineConfig(Url("http://$host:$port")), 16)
    }

    private suspend fun startServer(
        session: (suspend DefaultWebSocketServerSession.() -> Unit)? = null,
        frameSize: Long = Long.MAX_VALUE
    ): Job {
        val isConnected = MutableStateFlow(false)
        val plugin = createApplicationPlugin(name = "MonitoringPlugin") {
            on(MonitoringEvent(ApplicationStarted)) { _ ->
                isConnected.value = true
            }
        }

        val server = embeddedServer(CIO, port = port) {
            install(WebSockets)
            install(plugin)
            routing {
                webSocket("/") {
                    maxFrameSize = frameSize
                    if (session != null) {
                        session()
                    }
                }
            }
        }.start(wait = false)  // wait = true would block the start() call!

        // Wait for the server to start
        isConnected.first { it }

        // Don't use TestScope here, as this might get canceled after test execution!
        return CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) {
            server.stop(gracePeriodMillis = 0L)
        }
    }

    private fun receiverSession(receivedPackets: MutableSharedFlow<Packet>): suspend DefaultWebSocketServerSession.() -> Unit {
        val func: (suspend DefaultWebSocketServerSession.() -> Unit) = {
            val channel = ByteChannel(autoFlush = true)
            val reader = launch {
                while (!channel.isClosedForRead) {
                    receivedPackets.emit(channel.readPacket())
                }
            }
            for (frame in incoming) {
                if (frame.frameType == FrameType.BINARY) {
                    channel.writeFully(frame.readBytes())
                } else {
                    throw IllegalStateException("Received a non-binary frame")
                }
            }
            reader.cancel()
        }
        return func
    }

    private fun senderSession(channel: Channel<List<Packet>>): suspend DefaultWebSocketServerSession.() -> Unit {
        val func: (suspend DefaultWebSocketServerSession.() -> Unit) = {
            for (packets in channel) {
                if (packets.isEmpty()) {
                    break
                }
                for (packet in packets) {
                    with(Buffer()) {
                        write(packet)
                        outgoing.send(Frame.Binary(fin = true, packet = this))
                    }
                }
            }
        }
        return func
    }

    private fun senderSessionWithLimitedFrameSize(channel: Channel<List<Packet>>): suspend DefaultWebSocketServerSession.() -> Unit {
        val func: (suspend DefaultWebSocketServerSession.() -> Unit) = {
            for (packets in channel) {
                if (packets.isEmpty()) break
                for (packet in packets) {
                    with(Buffer()) {
                        write(packet)
                        val frame = Buffer()
                        var frames = 0
                        while (size > 0) {
                            frames++
                            readAtMostTo(frame, size.coerceAtMost(limitedFrameSize))
                            outgoing.send(Frame.Binary(fin = true, packet = frame))
                        }
                        println("Sent packet in $frames frames")
                    }
                }
            }
        }
        return func
    }
}