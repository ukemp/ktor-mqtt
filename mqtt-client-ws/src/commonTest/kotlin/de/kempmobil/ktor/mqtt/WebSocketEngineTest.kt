package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.toReasonString
import de.kempmobil.ktor.mqtt.util.toTopic
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WebSocketEngineTest {

    private val defaultHost = "localhost"
    private val defaultPort = 8080
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
    fun `when the engine sends packets they are received by server`() = runTest {
        val receivedPackets = MutableSharedFlow<Packet>(replay = 30)
        val closeServer = startServer(this, session = receiverSession(receivedPackets))

        val engine = MqttEngine()
        engine.start()
        samplePackets.forEach {
            engine.send(it)
        }

        val received = mutableListOf<Packet>()
        receivedPackets.take(samplePackets.size).toList(received)
        assertEquals(samplePackets, received)

        closeServer.start()
    }

    @Test
    fun `when the server sends packets they are received by the engine`() = runTest {
        val packetsToSend = MutableSharedFlow<Packet>(replay = 30)
        samplePackets.forEach { packetsToSend.emit(it) }

        val closeServer = startServer(this, session = senderSession(packetsToSend))
        val engine = MqttEngine()
        engine.start()

        val received = mutableListOf<Packet>()
        engine.packetResults.take(samplePackets.size).map { it.getOrThrow() }.toList(received)
        assertEquals(samplePackets, received)

        closeServer.start()
    }

    @Test
    fun `when the server sends packets in more than one frame they are received by the client`() = runTest {
        val packetsToSend = MutableSharedFlow<Packet>(replay = 30)
        samplePackets.forEach { packetsToSend.emit(it) }

        val closeServer = startServer(
            this,
            session = senderSessionWithLimitedFrameSize(packetsToSend),
            frameSize = limitedFrameSize
        )
        val engine = MqttEngine()
        engine.start()

        val received = mutableListOf<Packet>()
        engine.packetResults.take(samplePackets.size).map { it.getOrThrow() }.toList(received)
        assertEquals(samplePackets, received)

        closeServer.start()

    }

    @Suppress("TestFunctionName")
    private fun MqttEngine(): MqttEngine {
        return WebSocketEngine(WebSocketEngineConfig(Url("http://$defaultHost:$defaultPort")))
    }

    private fun startServer(
        testScope: TestScope,
        session: (suspend DefaultWebSocketServerSession.() -> Unit)? = null,
        frameSize: Long = Long.MAX_VALUE
    ): Job {
        val server = embeddedServer(CIO, port = defaultPort) {
            install(WebSockets)
            routing {
                webSocket("/") {
                    maxFrameSize = frameSize
                    if (session != null) {
                        session()
                    }
                }
            }
        }.start(wait = false)

        return testScope.launch(start = CoroutineStart.LAZY) {
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

    private fun senderSession(packets: MutableSharedFlow<Packet>): suspend DefaultWebSocketServerSession.() -> Unit {
        val func: (suspend DefaultWebSocketServerSession.() -> Unit) = {
            packets.collect { packet ->
                with(Buffer()) {
                    write(packet)
                    outgoing.send(Frame.Binary(fin = true, packet = this))
                }
            }
        }
        return func
    }

    private fun senderSessionWithLimitedFrameSize(packets: MutableSharedFlow<Packet>): suspend DefaultWebSocketServerSession.() -> Unit {
        val func: (suspend DefaultWebSocketServerSession.() -> Unit) = {
            packets.collect { packet ->
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
        return func
    }
}