package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.toTopic
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.ofType
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        connectionState.emit(true)

        val client = createClient(connection)
        val result = sendConnack(
            topicAliasMaximum = TopicAliasMaximum(42u),
            maximumQoS = MaximumQoS(1)
        ) {
            client.connect()
        }

        assertTrue(result.isSuccess)
        assertEquals(42u, client.serverTopicAliasMaximum.value)
        assertEquals(QoS.AT_LEAST_ONCE, client.maxQos)

        val state = client.connectionState.first()
        assertIs<Connected>(state)
        assertEquals(TopicAliasMaximum(42u), state.connack.topicAliasMaximum)
        assertEquals(MaximumQoS(1), state.connack.maximumQoS)
    }

    @Test
    fun `assigned client ID overrides the local client ID if empty`() = runTest {
        // See also MQTT-3.2.2-16
        everySuspend { connection.start() } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Connect>()) } returns Result.success(Unit)

        connectionState.emit(true)

        val client = createClient(connection, "")
        val result = sendConnack(assignedClientIdentifier = AssignedClientIdentifier("server-client-id")) {
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

        connectionState.emit(true)

        val client = createClient(connection)
        val result = sendConnack(reason = NotAuthorized) {
            client.connect()
        }

        assertTrue(result.isSuccess)

        val state = client.connectionState.first()
        assertIs<Disconnected>(state)

        verifySuspend { connection.disconnect() }
    }

    @Test
    fun `publish fails when receive maximum is exceeded`() = runTest {
        listOf(QoS.AT_LEAST_ONCE, QoS.EXACTLY_ONE).forEach { qoS ->
            val sendCounter = Channel<Int>(capacity = 20)
            everySuspend { connection.start() } returns Result.success(Unit)
            everySuspend { connection.send(ofType<Connect>()) } returns Result.success(Unit)
            everySuspend { connection.send(ofType<Publish>()) } calls {
                sendCounter.send(1)
                Result.success(Unit)
            }

            connectionState.emit(true)

            val client = createClient(connection)
            sendConnack(receiveMaximum = ReceiveMaximum(4u)) {
                client.connect()
            }

            val scope = CoroutineScope(Dispatchers.Default)

            // Send 10 PUBLISH requests which must not affect the ReceiveMaximum (AT_MOST_ONCE):
            repeat(10) {
                scope.launch {
                    client.publish(PublishRequest("topic") { desiredQoS = QoS.AT_MOST_ONCE })
                }
            }
            // Send 4 PUBLISH requests which count for the ReceiveMaximum:
            val request = PublishRequest("topic") { desiredQoS = qoS }
            repeat(4) {
                scope.launch {
                    client.publish(request)
                }
            }
            // Wait for all PUBLISH requests to be sent:
            repeat(14) {
                sendCounter.receive()
            }

            // Finally, the last request must fail:
            val result = client.publish(request)
            assertFalse(result.isSuccess)
            assertIs<ReceiveMaximumExceededException>(result.exceptionOrNull())
            assertEquals(4u, (result.exceptionOrNull() as ReceiveMaximumExceededException).receiveMaximum)
        }
    }

    @Test
    fun `send quota is reset for every successfully delivered publish packet`() = runTest {
        val packetIdentifier = Channel<UShort>()
        everySuspend { connection.start() } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Connect>()) } returns Result.success(Unit)
        everySuspend { connection.send(ofType<Publish>()) } calls {
            packetIdentifier.send((it.arg(0) as Publish).packetIdentifier!!)
            Result.success(Unit)
        }

        connectionState.emit(true)

        val client = createClient(connection)
        sendConnack(receiveMaximum = ReceiveMaximum(1u)) {
            client.connect()
        }

        val scope = CoroutineScope(Dispatchers.Default)

        val request = PublishRequest("topic") { desiredQoS = QoS.AT_LEAST_ONCE }
        repeat(10) {
            scope.launch {
                // With ReceiveMaximum set to 1, this should fail for the second try if send quota is not increased
                val result = client.publish(request)
                assertTrue(result.isSuccess)
            }
            // packetIdentifier.receive() will wait for the packet to be sent!
            val puback = Puback(packetIdentifier.receive(), Success)
            packetResults.emit(Result.success(puback))
        }
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

    // ---- HELPER functions -------------------------------------------------------------------------------------------

    private fun createClient(connection: MqttEngine, id: String? = null): MqttClient {
        val config = buildConfig(DefaultEngineFactory("", 0)) {
            connection { }
            ackMessageTimeout = 100.milliseconds
            clientId = id ?: ""
        }
        return MqttClient(config, connection, InMemoryPacketStore())
    }

    private suspend fun <T> TestScope.sendConnack(
        isSessionPresent: Boolean = false,
        reason: ReasonCode = Success,
        sessionExpiryInterval: SessionExpiryInterval? = null,
        receiveMaximum: ReceiveMaximum? = null,
        maximumQoS: MaximumQoS? = null,
        retainAvailable: RetainAvailable? = null,
        maximumPacketSize: MaximumPacketSize? = null,
        assignedClientIdentifier: AssignedClientIdentifier? = null,
        topicAliasMaximum: TopicAliasMaximum? = null,
        reasonString: ReasonString? = null,
        userProperties: UserProperties = UserProperties.EMPTY,
        wildcardSubscriptionAvailable: WildcardSubscriptionAvailable? = null,
        subscriptionIdentifierAvailable: SubscriptionIdentifierAvailable? = null,
        sharedSubscriptionAvailable: SharedSubscriptionAvailable? = null,
        serverKeepAlive: ServerKeepAlive? = null,
        responseInformation: ResponseInformation? = null,
        serverReference: ServerReference? = null,
        authenticationMethod: AuthenticationMethod? = null,
        authenticationData: AuthenticationData? = null,
        block: suspend CoroutineScope.() -> T
    ): T {
        val connack = Connack(
            isSessionPresent = isSessionPresent,
            reason = reason,
            sessionExpiryInterval = sessionExpiryInterval,
            receiveMaximum = receiveMaximum,
            maximumQoS = maximumQoS,
            retainAvailable = retainAvailable,
            maximumPacketSize = maximumPacketSize,
            assignedClientIdentifier = assignedClientIdentifier,
            topicAliasMaximum = topicAliasMaximum,
            reasonString = reasonString,
            userProperties = userProperties,
            wildcardSubscriptionAvailable = wildcardSubscriptionAvailable,
            subscriptionIdentifierAvailable = subscriptionIdentifierAvailable,
            sharedSubscriptionAvailable = sharedSubscriptionAvailable,
            serverKeepAlive = serverKeepAlive,
            responseInformation = responseInformation,
            serverReference = serverReference,
            authenticationMethod = authenticationMethod,
            authenticationData = authenticationData,
        )
        return sendPacket(connack, block)
    }

    /**
     * Executes the specified coroutine asynchronously and then sends the specified packet to the `packetResults` flow.
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