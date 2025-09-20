package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.toTopic
import dev.mokkery.*
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import dev.mokkery.matcher.ofType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * This test is abstract, as mocking does not work reliable in WASM yet. Inheriting test classes are available in
 * jvmTest and nativeTest.
 */
@OptIn(ExperimentalTime::class)
abstract class MqttClientTest {

    private lateinit var connection: MqttEngine

    private lateinit var connectionState: MutableStateFlow<Boolean>

    private lateinit var packetResults: MutableSharedFlow<Result<Packet>>

    private lateinit var session: SessionStore

    @BeforeTest
    fun setup() {
        connectionState = MutableStateFlow(false)
        packetResults = MutableSharedFlow()

        connection = mock {
            every { connected } returns connectionState
            every { packetResults } returns this@MqttClientTest.packetResults
        }

        session = mock {
            every { clear() } returns Unit
            every { unacknowledgedPackets() } returns emptyList()
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

        val filters = listOf("test/topic".toTopic())
        val client = createClient(connection)
        val result = client.unsubscribe(filters)

        assertTrue(result.isFailure)
        assertIs<ConnectionException>(result.exceptionOrNull())
    }

    @Test
    fun `unsubscribe fails when the unsuback packet is not received`() = runTest {
        everySuspend { connection.send(ofType<Unsubscribe>()) } returns Result.success(Unit)

        val filters = listOf("test/topic".toTopic())
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
        val filters = listOf("test/topic".toTopic())
        val client = createClient(connection)
        val result = sendPacket(unsuback) {
            client.unsubscribe(filters)
        }

        assertTrue(result.isSuccess)
        assertSame(unsuback, result.getOrNull())
    }

    // ---- PUBLISH tests ----------------------------------------------------------------------------------------------

    @Test
    fun `publish a message when not connected returns error`() = runTest {
        val client = createClient(connection)
        val result = client.publish(PublishRequest("test/topic") { })

        assertFalse { result.isSuccess }
    }

    @Test
    fun `publish a QoS 0 message when sending fails returns error`() = runTest {
        val client = createClient(connection)
        connectionState.emit(true)
        everySuspend { connection.send(ofType<Publish>()) } returns Result.failure(ConnectionException())

        val result = client.publish(PublishRequest("test/topic") {
            desiredQoS = QoS.AT_MOST_ONCE
        })

        assertFalse { result.isSuccess }
        assertIs<ConnectionException>(result.exceptionOrNull())
    }

    @Test
    fun `publish a QoS 1 or 2 message when sending fails returns error and saves packet in session store`() = runTest {
        val client = createClient(connection)
        connectionState.emit(true)
        everySuspend { connection.send(ofType<Publish>()) } returns Result.failure(ConnectionException())
        every { session.store(any()) } calls { (publish: Publish) ->
            InFlightPublish(publish, Clock.System.now(), 1)
        }

        listOf(QoS.AT_LEAST_ONCE, QoS.EXACTLY_ONE).forEach { qoS ->
            val result = client.publish(PublishRequest("test/topic") {
                desiredQoS = qoS
            })

            assertFalse { result.isSuccess }
            assertIs<ConnectionException>(result.exceptionOrNull())
            verify { session.store(any()) }
        }
    }

    @Test
    fun `publish a message with QoS 0`() = runTest {
        val client = createClient(connection)
        connectionState.emit(true)
        everySuspend { connection.send(ofType<Publish>()) } returns Result.success(Unit)

        val result = client.publish(PublishRequest("test/topic") {
            desiredQoS = QoS.AT_MOST_ONCE
        })

        assertTrue { result.isSuccess }
        assertEquals("test/topic", result.getOrThrow().source.topic.name)
    }

    @Test
    fun `publish a message with QoS 1`() = runTest {
        var inFlightPacket: InFlightPublish? = null
        val client = createClient(connection)
        connectionState.emit(true)
        packetResults.emit(Result.success(Puback(1u, Success)))
        everySuspend { connection.send(ofType<Publish>()) } returns Result.success(Unit)
        every { session.store(any()) } calls { (publish: Publish) ->
            InFlightPublish(publish, Clock.System.now(), 1).also { inFlightPacket = it }
        }
        every { session.acknowledge(any()) } returns Unit

        val result = client.publish(PublishRequest("test/topic") {
            desiredQoS = QoS.AT_LEAST_ONCE
        })

        assertTrue { result.isSuccess }
        assertEquals("test/topic", result.getOrThrow().source.topic.name)
        assertNotNull(inFlightPacket)
        verify { session.store(inFlightPacket.source) }
        verify { session.acknowledge(inFlightPacket) }
    }

    @Test
    fun `publish a message with QoS 2`() = runTest {
        var inFlightPublish: InFlightPublish? = null
        var inFlightPubrel: InFlightPubrel? = null
        val client = createClient(connection)
        connectionState.emit(true)
        packetResults.emit(Result.success(Pubrec(1u, Success)))
        packetResults.emit(Result.success(Pubcomp(1u, Success)))
        everySuspend { connection.send(ofType<Publish>()) } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Pubrel>()) } returns Result.success(Unit)
        every { session.store(any()) } calls { (publish: Publish) ->
            InFlightPublish(publish, Clock.System.now(), 1).also { inFlightPublish = it }
        }
        every { session.replace(any()) } calls { (inFlightPublish: InFlightPublish) ->
            InFlightPubrel(inFlightPublish, 1.toLong()).also { inFlightPubrel = it }
        }
        every { session.acknowledge(any()) } returns Unit

        val result = client.publish(PublishRequest("test/topic") {
            desiredQoS = QoS.EXACTLY_ONE
        })

        assertTrue { result.isSuccess }
        assertEquals("test/topic", result.getOrThrow().source.topic.name)
        assertNotNull(inFlightPublish)
        assertNotNull(inFlightPubrel)
        verify { session.store(inFlightPublish.source) }
        verify { session.replace(inFlightPublish) }
        verify { session.acknowledge(inFlightPubrel) }
    }

    // ---- Helper functions -------------------------------------------------------------------------------------------

    private fun createClient(connection: MqttEngine, id: String? = null): MqttClient {
        val config = buildConfig(DefaultEngineFactory("", 0)) {
            connection { }
            ackMessageTimeout = 100.milliseconds
            clientId = id ?: ""
        }
        return MqttClient(config, connection, session)
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