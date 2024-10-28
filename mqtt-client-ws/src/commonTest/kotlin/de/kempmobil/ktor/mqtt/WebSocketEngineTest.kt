package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.readPacket
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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
        val closeServer = startServer(this, session = createEchoSession(serverPackets))

        val expected = Publish(topic = Topic("test-topic"), payload = "1234567890".encodeToByteString())
        val engine = MqttEngine()
        engine.start()
        engine.send(expected)

        val actual = serverPackets.first()
        assertEquals(expected, actual)

        closeServer.start()
    }

    @Suppress("TestFunctionName")
    private fun MqttEngine(): MqttEngine {
        return WebSocketEngine(WebSocketEngineConfig(Url("http://$defaultHost:$defaultPort/mqtt")))
    }

    private fun startServer(
        testScope: TestScope,
        session: (suspend DefaultWebSocketServerSession.() -> Unit)? = null
    ): Job {
        val server = embeddedServer(CIO, port = defaultPort) {
            install(WebSockets)
            routing {
                webSocket("/mqtt") {
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

    private fun createEchoSession(receivedPackets: MutableSharedFlow<Packet>): suspend DefaultWebSocketServerSession.() -> Unit {
        val func: (suspend DefaultWebSocketServerSession.() -> Unit) = {
            for (frame in incoming) {
                if (frame is Frame.Binary) {
                    with(Buffer()) {
                        writeFully(frame.readBytes())
                        receivedPackets.emit(readPacket())
                    }
                } else {
                    throw IllegalStateException("Received a non-binary frame")
                }
            }
        }
        return func
    }
}