package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Connack
import de.kempmobil.ktor.mqtt.packet.Connect
import de.kempmobil.ktor.mqtt.packet.Packet
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.ofType
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds


class MqttClientTest {

    private lateinit var connection: MqttConnection

    private lateinit var connectionState: MutableStateFlow<Boolean>

    private lateinit var packetResults: MutableSharedFlow<Result<Packet>>

    @BeforeTest
    fun setup() {
        connectionState = MutableStateFlow(false)
        packetResults = MutableSharedFlow()


        connection = mock {
            every { connected } returns connectionState
            every { packetResults } returns this@MqttClientTest.packetResults
        }
    }

    @Test
    fun `when connection fails to start return a failure result`() = runTest {
        everySuspend { connection.start() } returns Result.failure(ConnectionException())

        val client = createClient(connection)
        val result = client.connect()

        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())
    }

    @Test
    fun `when sending of connect packet fails return a failure result`() = runTest {
        val connectionState = MutableStateFlow(false)
        val results = MutableSharedFlow<Result<Packet>>()

        val connection = mock<MqttConnection> {
            every { connected } returns connectionState
            every { packetResults } returns results
        }

        everySuspend { connection.start() } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Connect>()) } returns Result.failure(ConnectionException())

        val client = createClient(connection)
        val result = client.connect()

        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())
    }

    @Test
    fun `when server does not send CONNACK return a failure result`() = runTest {
        everySuspend { connection.start() } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Connect>()) } returns Result.success(Unit)

        val client = createClient(connection)
        val result = client.connect()

        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())
    }

    @Test
    fun `return CONNACK on successful connection attempt`() = runTest {
        everySuspend { connection.start() } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Connect>()) } returns Result.success(Unit)

        val connack = Connack(
            isSessionPresent = false,
            reason = Success,
            topicAliasMaximum = TopicAliasMaximum(42u),
            maximumQoS = MaximumQoS(1)
        )
        connectionState.emit(true)

        val client = createClient(connection)
        val result = sendPacket(connack) {
            client.connect()
        }

        assertTrue(result.isSuccess)
        assertEquals(connack, result.getOrNull())
        assertEquals(42u, client.serverTopicAliasMaximum.value)
        assertEquals(QoS.AT_LEAST_ONCE, client.maxQos)

        val state = client.connectionState.first()
        assertIs<Connected>(state)
        assertEquals(connack, state.connack)
    }

    @Test
    fun `when CONNACK contains error message the connection state should be disconnected`() = runTest {
        everySuspend { connection.start() } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Connect>()) } returns Result.success(Unit)
        everySuspend { connection.disconnect() } returns Unit

        val connack = Connack(
            isSessionPresent = false,
            reason = NotAuthorized,
            topicAliasMaximum = TopicAliasMaximum(42u),
            maximumQoS = MaximumQoS(1)
        )
        connectionState.emit(true)

        val client = createClient(connection)
        val result = sendPacket(connack) {
            client.connect()
        }

        assertTrue(result.isSuccess)

        val state = client.connectionState.first()
        assertIs<Disconnected>(state)

        verifySuspend { connection.disconnect() }
    }

    private fun createClient(connection: MqttConnection): MqttClient {
        val config = buildConfig("mock") {
            ackMessageTimeout = 100.milliseconds
        }
        return MqttClient(config, connection)
    }

    /**
     * Executes the specified coroutine asynchronously and the sends the specified packet to the `packetResults` flow.
     */
    private suspend fun <T> TestScope.sendPacket(packet: Packet, block: suspend CoroutineScope.() -> T): T {
        with(this) {
            val response = async {
                block()
            }
            testScheduler.advanceUntilIdle()
            packetResults.emit(Result.success(packet))
            return response.await()
        }
    }
}