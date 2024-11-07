package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.*
import de.kempmobil.ktor.mqtt.util.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds


public class MqttClient internal constructor(
    private val config: MqttClientConfig,
    private val engine: MqttEngine,
    private val packetStore: PacketStore
) {
    public constructor(config: MqttClientConfig) :
            this(config, config.engine, InMemoryPacketStore())

    private val _publishedPackets = MutableSharedFlow<Publish>()

    /**
     * Provides a flow of all packets published by the server.
     */
    public val publishedPackets: SharedFlow<Publish>
        get() = _publishedPackets

    /**
     * Returns the maximum QoS level allowed by the server, defaults to [QoS.EXACTLY_ONE] as long as no CONNACK packet
     * has been received.
     */
    public val maxQos: QoS
        get() = _maxQos
    private var _maxQos = QoS.EXACTLY_ONE

    /**
     * The ID of this client as defined in [MqttClientConfig] or the value of the assigned client ID of the [Connack]
     * packet, if [MqttClientConfig] contained an empty string.
     */
    public val clientId: String
        get() = _clientId
    private var _clientId = config.clientId

    /**
     * The server topic alias maximum value as contained the CONNACK message from the server (or the default value of 0)
     */
    public val serverTopicAliasMaximum: TopicAliasMaximum
        get() = _serverTopicAliasMaximum
    private var _serverTopicAliasMaximum: TopicAliasMaximum = TopicAliasMaximum(0u)

    /**
     * The value of 'Subscription Identifiers Available' from the CONNACK message of the server.
     */
    public val subscriptionIdentifierAvailable: Boolean
        get() = _subscriptionIdentifierAvailable
    private var _subscriptionIdentifierAvailable = true

    /**
     * Provides the connection state of this MQTT client. When the state is [Connected] this implies that an IP
     * connectivity has been established AND that the server responded with a success CONNACK message.
     */
    public val connectionState: Flow<ConnectionState>
        get() = engine.connected.combine(connackFlow) { isConnected: Boolean, connack: Connack? ->
            if (isConnected && (connack?.isSuccess == true)) {
                Connected(connack)
            } else {
                Disconnected
            }
        }.distinctUntilChanged()

    private val connackFlow = MutableStateFlow<Connack?>(null)

    private val scope = CoroutineScope(config.dispatcher)

    private val receivedPackets = MutableSharedFlow<Packet>()

    private var packetIdentifier: UShort = 0u
    private val packetIdentifierMutex = Mutex()

    private val publishReceivedPackets = mutableMapOf<UShort, Pubrec>()

    private val isCleanStart: Boolean
        get() = true // TODO

    init {
        scope.launch {
            engine.packetResults.collect { result ->
                handlePacketResult(result)
            }
        }
    }

    /**
     * Tries to connect to the MQTT server and send a CONNECT message.
     *
     * @return the connection result. Note that even when the result returns a Connack packet, the client may still not
     *         be successfully connected, as the server may send a CONNACK with an error message.
     * @see connectionState
     */
    public suspend fun connect(): Result<Connack> {
        connackFlow.emit(null)

        return engine.start()
            .mapCatching {
                awaitResponseOf<Connack>(PacketType.CONNACK) {
                    engine.send(createConnect())
                }.onSuccess {
                    inspectConnack(it)
                }.getOrElse() {
                    throw it
                }
            }
    }

    /**
     * Sends a SUBSCRIBE request to the MQTT server for the list of topics contained in [filters].
     *
     * @param filters the filters to subscribe to
     * @param subscriptionIdentifier an optional subscription identifier for this subscribe request. Note that a
     *        non-null value will be ignored, when the server does not support subscription identifiers.
     * @return the SUBACK packet if the subscribe request was answered by the server. Note that the SUBACK may still
     *         contain error messages for each of the subscribed topics.
     * @see Suback.hasFailure
     * @see subscriptionIdentifierAvailable
     */
    public suspend fun subscribe(
        filters: List<TopicFilter>,
        subscriptionIdentifier: SubscriptionIdentifier? = null,
        userProperties: UserProperties = UserProperties.EMPTY
    ): Result<Suback> {
        val identifier = if ((subscriptionIdentifier != null) && !subscriptionIdentifierAvailable) {
            Logger.w(
                throwable = IllegalArgumentException("Ignoring $subscriptionIdentifier"),
                { "Ignoring subscription identifier, as the server doesn't support it" }
            )
            null
        } else {
            subscriptionIdentifier
        }
        val subscribe = createSubscribe(filters, identifier, userProperties)

        return awaitResponseOf({ it.isResponseFor<Suback>(subscribe) }, {
            engine.send(subscribe)
        })
    }

    public suspend fun unsubscribe(
        topics: List<Topic>,
        userProperties: UserProperties = UserProperties.EMPTY
    ): Result<Unsuback> {
        val unsubscribe = createUnsubscribe(topics, userProperties)

        return awaitResponseOf({ it.isResponseFor<Unsuback>(unsubscribe) }, {
            engine.send(unsubscribe)
        })
    }

    public suspend fun publish(request: PublishRequest): Result<QoS> {
        if (!engine.connected.value) {
            return Result.failure(ConnectionException("Cannot send PUBLISH packet while not connected"))
        }

        return createPublish(request).map { publish ->

            when (publish.qoS) {
                QoS.AT_MOST_ONCE -> {
                    engine.send(publish)
                    QoS.AT_MOST_ONCE
                }

                QoS.AT_LEAST_ONCE -> {
                    packetStore.store(publish)
                    engine.send(publish)
                    receivedPackets.first { it.isResponseFor<Puback>(publish) }
                    packetStore.acknowledge(publish)
                    QoS.AT_LEAST_ONCE
                }

                QoS.EXACTLY_ONE -> {
                    packetStore.store(publish)
                    engine.send(publish)
                    receivedPackets.first { it.isResponseFor<Pubrec>(publish) }
                    val pubrel = packetStore.replace(publish)
                    engine.send(pubrel)
                    receivedPackets.first { it.isResponseFor<Pubcomp>(publish) }
                    packetStore.acknowledge(pubrel)
                    QoS.EXACTLY_ONE
                }
            }
        }
    }

    public suspend fun disconnect(reasonCode: ReasonCode = NormalDisconnection, reason: String? = null) {
        engine.send(createDisconnect(reasonCode, reason))
        engine.disconnect()
    }

    public fun close() {
        engine.close()
        scope.cancel()
    }

    // ---- Helper methods ---------------------------------------------------------------------------------------------

    private fun createConnect(): Connect {
        return Connect(
            isCleanStart = isCleanStart,
            willMessage = config.willMessage,
            willOqS = config.willOqS,
            retainWillMessage = config.retainWillMessage,
            keepAliveSeconds = config.keepAliveSeconds,
            clientId = config.clientId,
            userName = config.username,
            password = config.password,
            sessionExpiryInterval = config.sessionExpiryInterval,
            receiveMaximum = config.receiveMaximum,
            maximumPacketSize = config.maximumPacketSize,
            topicAliasMaximum = config.topicAliasMaximum,
            requestResponseInformation = config.requestResponseInformation,
            requestProblemInformation = config.requestProblemInformation,
            userProperties = config.userProperties,
            authenticationMethod = config.authenticationMethod,
            authenticationData = config.authenticationData
        )
    }

    private suspend fun createSubscribe(
        filters: List<TopicFilter>,
        subscriptionIdentifier: SubscriptionIdentifier?,
        userProperties: UserProperties
    ): Subscribe {
        return Subscribe(
            packetIdentifier = nextPacketIdentifier(),
            filters = filters,
            subscriptionIdentifier = subscriptionIdentifier,
            userProperties = userProperties
        )
    }

    private suspend fun createUnsubscribe(topics: List<Topic>, userProperties: UserProperties): Unsubscribe {
        return Unsubscribe(
            packetIdentifier = nextPacketIdentifier(),
            topics = topics,
            userProperties = userProperties
        )
    }

    private suspend fun createPublish(request: PublishRequest, isDupMessage: Boolean = false): Result<Publish> {
        return if (request.topicAlias != null && request.topicAlias.value > serverTopicAliasMaximum.value) {
            Result.failure(TopicAliasException("Server maximum topic alias is: $serverTopicAliasMaximum, but you requested: ${request.topicAlias}"))
        } else if (request.topic.containsWildcard()) {
            Result.failure(IllegalArgumentException("The topic of a PUBLISH packet must not contain wildcard characters: '${request.topic}'"))
        } else {
            val actualQoS = request.desiredQoS.coerceAtMost(maxQos)  // MQTT-3.2.2-11
            if (actualQoS != request.desiredQoS) {
                Logger.i { "Publish QoS for ${request.topic} was ${request.desiredQoS} but was downgraded to $actualQoS due to server requirements" }
            }
            Result.success(
                Publish(
                    isDupMessage = if (actualQoS == QoS.AT_MOST_ONCE) false else isDupMessage,  // MQTT-3.3.1-2
                    qoS = actualQoS,
                    isRetainMessage = request.isRetainMessage,
                    packetIdentifier = if (actualQoS == QoS.AT_MOST_ONCE) null else nextPacketIdentifier(),
                    topic = request.topic,
                    payloadFormatIndicator = request.payloadFormatIndicator,
                    messageExpiryInterval = request.messageExpiryInterval,
                    topicAlias = request.topicAlias,
                    responseTopic = request.responseTopic,
                    correlationData = request.correlationData,
                    userProperties = request.userProperties,
                    subscriptionIdentifier = null, // A PUBLISH packet sent from a Client to a Server MUST NOT contain a Subscription Identifier [MQTT-3.3.4-6]
                    contentType = request.contentType,
                    payload = request.payload
                )
            )
        }
    }

    private fun createDisconnect(reasonCode: ReasonCode, reason: String?): Disconnect {
        return Disconnect(
            reason = reasonCode,
            sessionExpiryInterval = config.sessionExpiryInterval,
            reasonString = reason.toReasonString()
        )
    }

    private suspend fun inspectConnack(connack: Connack): Connack {
        connackFlow.emit(connack)

        if (!connack.isSuccess) {
            Logger.i { "Server sent CONNACK packet with ${connack.reason}, hence terminating the connection" }
            engine.disconnect()
        } else {
            connack.maximumQoS?.let {
                _maxQos = it.qoS
            }
            _serverTopicAliasMaximum = connack.topicAliasMaximum ?: TopicAliasMaximum(0u)

            val keepAlive = (connack.serverKeepAlive?.value ?: config.keepAliveSeconds).toInt().seconds
            if (keepAlive.inWholeSeconds > 0) {
                scope.launch {
                    delay(keepAlive)
                    awaitResponseOf<Pingresp>(PacketType.PINGRESP) {
                        engine.send(Pingreq)
                    }
                }
            }

            // MQTT-3.2.2-16
            if (config.clientId.isEmpty()) {
                connack.assignedClientIdentifier?.let { _clientId = it.value }
            }

            _subscriptionIdentifierAvailable = connack.subscriptionIdentifierAvailable.isAvailable()

            Logger.i {
                "Received server parameters: " +
                        "maxQoS=$maxQos, " +
                        "keepAlive=$keepAlive, " +
                        "serverTopicAliasMaximum=${serverTopicAliasMaximum.value}, " +
                        "assignedClientIdentifier=${connack.assignedClientIdentifier?.value ?: "''"}, " +
                        "subscriptionIdentifierAvailable=$_subscriptionIdentifierAvailable"
            }
        }

        return connack
    }

    private suspend fun handlePacketResult(result: Result<Packet>) {
        result.onSuccess { packet ->
            handlePacket(packet)
        }.onFailure { throwable ->
            if (throwable is MalformedPacketException) {
                Logger.w { "Received malformed packet: '${throwable.message}', disconnecting..." }
            } else {
                Logger.e(throwable = throwable) { "Unexpected error while parsing a packet, disconnecting..." }
            }
            engine.disconnect()
        }
    }

    private suspend fun handlePacket(packet: Packet) {
        Logger.v { "Received packet: $packet" }
        when (packet) {
            is Disconnect -> {
                Logger.i { "Received DISCONNECT (${packet.reasonString.ifNull(packet.reason)}) from server, disconnecting..." }
                engine.disconnect()
            }

            is Publish -> {
                when (packet.qoS) {
                    QoS.AT_MOST_ONCE -> {
                        _publishedPackets.emit(packet)
                    }

                    QoS.AT_LEAST_ONCE -> {
                        _publishedPackets.emit(packet)
                        engine.send(Puback.from(packet))
                    }

                    QoS.EXACTLY_ONE -> {
                        val id = packet.packetIdentifier!!
                        if (publishReceivedPackets.containsKey(id)) {
                            // Must resend the PUBREC packet
                            publishReceivedPackets[id]?.let {
                                engine.send(it)
                            }
                        } else {
                            _publishedPackets.emit(packet)
                            val pubrec = Pubrec.from(packet)
                            publishReceivedPackets[id] = pubrec
                            engine.send(pubrec)
                        }
                    }
                }
            }

            is Pubrel -> {
                engine.send(Pubcomp.from(packet))
                publishReceivedPackets.remove(packet.packetIdentifier)
            }

            else -> {
                receivedPackets.emit(packet)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <P : Packet> awaitResponseOf(
        predicate: suspend (Packet) -> Boolean,
        request: suspend () -> Result<Unit>
    ): Result<P> {
        val waitForResponse = scope.async {
            val response = withTimeoutOrNull(config.ackMessageTimeout) {
                (receivedPackets.first(predicate) as P)
            }
            if (response != null) {
                Result.success(response)
            } else {
                Result.failure(TimeoutException("Didn't receive requested packet within ${config.ackMessageTimeout}"))
            }
        }
        request().onFailure { return Result.failure(it) }

        return waitForResponse.await()
    }

    private suspend fun <P : Packet> awaitResponseOf(type: PacketType, request: suspend () -> Result<Unit>): Result<P> {
        return awaitResponseOf({ it.type == type }, request)
    }

    private suspend fun nextPacketIdentifier(): UShort {
        return packetIdentifierMutex.withLock {
            packetIdentifier = (packetIdentifier + 1u).toUShort()
            if (packetIdentifier == 0u.toUShort()) {
                packetIdentifier = 1u
            }
            packetIdentifier
        }
    }
}

/**
 * Creates a new MQTT client, connecting to the specified host on the specified port.
 *
 * To enable TLS on the connection, use the following code snippet:
 * ```
 * MqttClient("test.mosquitto.org", 8886) {
 *     connection {
 *         tls { }
 *     }
 *     ...
 * }
 * ```
 *
 * @sample Sample.createClient
 */
public fun MqttClient(
    host: String,
    port: Int,
    init: MqttClientConfigBuilder<DefaultEngineConfig>.() -> Unit
): MqttClient {
    return MqttClient(MqttClientConfigBuilder(DefaultEngineFactory(host, port)).also(init).build())
}

internal class Sample {

    internal fun createClient() {
        val client = MqttClient("test.mosquitto.org", 8886) {
            connection {
                tls { }  // Enable TLS using the system's trust manager
            }

            clientId = "test-client"
            username = "ro"
            password = "readonly"

            willMessage("topics/last-will") {
                retainWillMessage = true
                willOqS = QoS.AT_MOST_ONCE
                payload("Last will message of test-client")
                properties {
                    willDelayInterval = 10.seconds
                    messageExpiryInterval = 2.days
                }
            }
            userProperties {
                "user-key" to "value1"
                "user-key" to "value2"  // Property keys may occur more than once!
            }
        }
    }
}
