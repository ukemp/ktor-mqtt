package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.*
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

    private lateinit var connection: MqttEngine

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
    fun `connect fails when connection cannot be established`() = runTest {
        everySuspend { connection.start() } returns Result.failure(ConnectionException())

        val client = createClient(connection)
        val result = client.connect()

        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())
    }

    @Test
    fun `connect fails when the connect packet is not sent successfully`() = runTest {
        val connectionState = MutableStateFlow(false)
        val results = MutableSharedFlow<Result<Packet>>()

        val connection = mock<MqttEngine> {
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
    fun `connect fails when no connack packet is received`() = runTest {
        everySuspend { connection.start() } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Connect>()) } returns Result.success(Unit)

        val client = createClient(connection)
        val result = client.connect()

        assertTrue(result.isFailure)
        assertIs<TimeoutException>(result.exceptionOrNull())
    }

    @Test
    fun `connect succeeds when receiving successful connack packet`() = runTest {
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
    fun `assigned client ID overrides the local client ID if empty`() = runTest {
        // See also MQTT-3.2.2-16
        everySuspend { connection.start() } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Connect>()) } returns Result.success(Unit)

        val connack = Connack(
            isSessionPresent = false,
            reason = Success,
            assignedClientIdentifier = AssignedClientIdentifier("server-client-id")
        )
        connectionState.emit(true)

        val client = createClient(connection, "")
        val result = sendPacket(connack) {
            client.connect()
        }

        assertTrue(result.isSuccess)
        assertEquals("server-client-id", client.clientId)
    }

    @Test
    fun `connect fails when receiving unsuccessful connack packet and client disconnects`() = runTest {
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

    // ---- SUBSCRIBE tests --------------------------------------------------------------------------------------------

    @Test
    fun `subscribe fails when the subscribe packet is not sent successfully`() = runTest {
        everySuspend { connection.send(ofType<Subscribe>()) } returns Result.failure(ConnectionException())

        val filters = buildFilterList { add("test/topic") }
        val client = createClient(connection)
        val result = client.subscribe(filters)

        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())
    }

    @Test
    fun `subscribe fails when the suback packet is not received`() = runTest {
        everySuspend { connection.send(ofType<Subscribe>()) } returns Result.success(Unit)

        val filters = buildFilterList { add("test/topic") }
        val client = createClient(connection)
        val result = client.subscribe(filters)

        assertTrue(result.isFailure)
        assertIs<TimeoutException>(result.exceptionOrNull())
    }

    @Test
    fun `subscribe succeeds when receiving suback packet`() = runTest {
        everySuspend { connection.send(ofType<Subscribe>()) } returns Result.success(Unit)

        val suback = Suback(
            packetIdentifier = 1u,
            reasons = listOf(GrantedQoS0, TopicFilterInvalid)
        )
        val filters = buildFilterList {
            add("test/topic1")
            add("test/topic2")
        }
        val client = createClient(connection)
        val result = sendPacket(suback) {
            client.subscribe(filters)
        }

        assertTrue(result.isSuccess)
        assertSame(suback, result.getOrNull())
    }

    // ---- UNSUBSCRIBE tests ------------------------------------------------------------------------------------------

    @Test
    fun `unsubscribe fails when the unsubscribe packet is not sent successfully`() = runTest {
        everySuspend { connection.send(ofType<Unsubscribe>()) } returns Result.failure(ConnectionException())

        val filters = listOf(Topic("test/topic"))
        val client = createClient(connection)
        val result = client.unsubscribe(filters)

        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())
    }

    @Test
    fun `unsubscribe fails when the unsuback packet is not received`() = runTest {
        everySuspend { connection.send(ofType<Unsubscribe>()) } returns Result.success(Unit)

        val filters = listOf(Topic("test/topic"))
        val client = createClient(connection)
        val result = client.unsubscribe(filters)

        assertTrue(result.isFailure)
        assertIs<TimeoutException>(result.exceptionOrNull())
    }

    @Test
    fun `unsubscribe succeeds when receiving unsuback packet`() = runTest {
        everySuspend { connection.send(ofType<Unsubscribe>()) } returns Result.success(Unit)

        val unsuback = Unsuback(
            packetIdentifier = 1u,
            reasons = listOf(GrantedQoS0, TopicFilterInvalid)
        )
        val filters = listOf(Topic("test/topic"))
        val client = createClient(connection)
        val result = sendPacket(unsuback) {
            client.unsubscribe(filters)
        }

        assertTrue(result.isSuccess)
        assertSame(unsuback, result.getOrNull())
    }

    private fun createClient(connection: MqttEngine, id: String? = null): MqttClient {
        val config = buildConfig(DefaultEngineFactory) {
            connectTo("dummy") { }
            ackMessageTimeout = 100.milliseconds
            clientId = id ?: ""
        }
        return MqttClient(config, connection, InMemoryPacketStore())
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