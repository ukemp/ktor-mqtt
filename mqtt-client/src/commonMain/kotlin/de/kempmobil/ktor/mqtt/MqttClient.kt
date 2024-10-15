package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds


public class MqttClient internal constructor(
    private val config: MqttClientConfig,
    private val connection: MqttConnection,
    private val packetStore: PacketStore
) {
    public constructor(config: MqttClientConfig) : this(config, MqttConnectionImpl(config), InMemoryPacketStore())

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
     * Provides the connection state of this MQTT client. When the state is [Connected] this implies that an IP
     * connectivity has been established AND that the server responded with a success CONNACK message.
     */
    public val connectionState: Flow<ConnectionState>
        get() = connection.connected.combine(connackFlow) { isConnected: Boolean, connack: Connack? ->
            if (isConnected && (connack?.isSuccess == true)) {
                Connected(connack)
            } else {
                Disconnected
            }
        }

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
            connection.packetResults.collect { result ->
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

        return connection.start()
            .mapCatching {
                awaitResponseOf<Connack>(PacketType.CONNACK) {
                    connection.send(createConnect())
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
     * @return the SUBACK packet if the subscribe request was answered by the server. Note that the SUBACK may still
     *         contain error messages for each of the subscribed topics.
     * @see Suback.hasFailure
     */
    public suspend fun subscribe(
        filters: List<TopicFilter>,
        subscriptionIdentifier: SubscriptionIdentifier? = null,
        userProperties: UserProperties = UserProperties.EMPTY
    ): Result<Suback> {
        val subscribe = createSubscribe(filters, subscriptionIdentifier, userProperties)

        return awaitResponseOf({ it.isResponseFor<Suback>(subscribe) }, {
            connection.send(subscribe)
        })
    }

    public suspend fun unsubscribe(
        topics: List<Topic>,
        userProperties: UserProperties = UserProperties.EMPTY
    ): Result<Unsuback> {
        val unsubscribe = createUnsubscribe(topics, userProperties)

        return awaitResponseOf({ it.isResponseFor<Unsuback>(unsubscribe) }, {
            connection.send(unsubscribe)
        })
    }

    public suspend fun publish(request: PublishRequest): Result<QoS> {
        return createPublish(request).map { publish ->

            when (publish.qoS) {
                QoS.AT_MOST_ONCE -> {
                    connection.send(publish)
                    QoS.AT_MOST_ONCE
                }

                QoS.AT_LEAST_ONCE -> {
                    packetStore.store(publish)
                    connection.send(publish)
                    receivedPackets.first { it.isResponseFor<Puback>(publish) }
                    packetStore.acknowledge(publish)
                    QoS.AT_LEAST_ONCE
                }

                QoS.EXACTLY_ONE -> {
                    packetStore.store(publish)
                    connection.send(publish)
                    receivedPackets.first { it.isResponseFor<Pubrec>(publish) }
                    val pubrel = packetStore.replace(publish)
                    connection.send(pubrel)
                    receivedPackets.first { it.isResponseFor<Pubcomp>(publish) }
                    packetStore.acknowledge(pubrel)
                    QoS.EXACTLY_ONE
                }
            }
        }
    }

    public suspend fun disconnect(reasonCode: ReasonCode = NormalDisconnection, reasonString: String? = null) {
        connection.send(createDisconnect(reasonCode, if (reasonString == null) null else ReasonString(reasonString)))
        connection.disconnect()
    }

    public fun close() {
        connection.close()
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

    private suspend fun createPublish(request: PublishRequest): Result<Publish> {
        return if (request.topicAlias != null && request.topicAlias.value > serverTopicAliasMaximum.value) {
            Result.failure(TopicAliasException("Server maximum topic alias is: $serverTopicAliasMaximum, but you requested: ${request.topicAlias}"))
        } else if (request.topic.containsWildcard()) {
            Result.failure(IllegalArgumentException("The topic of a PUBLISH packet must not contain wildcard characters: '${request.topic}'"))
        } else {
            Result.success(
                Publish(
                    isDupMessage = false,
                    qoS = request.desiredQoS.coerceAtMost(maxQos),  // MQTT-3.2.2-11
                    isRetainMessage = request.isRetainMessage,
                    packetIdentifier = nextPacketIdentifier(),
                    topic = request.topic,
                    payloadFormatIndicator = request.payloadFormatIndicator,
                    messageExpiryInterval = request.messageExpiryInterval,
                    topicAlias = request.topicAlias,
                    responseTopic = request.responseTopic,
                    correlationData = request.correlationData,
                    userProperties = request.userProperties,
                    contentType = request.contentType,
                    payload = request.payload
                )
            )
        }
    }

    private fun createDisconnect(reasonCode: ReasonCode, reasonString: ReasonString?): Disconnect {
        return Disconnect(
            reasonCode,
            sessionExpiryInterval = config.sessionExpiryInterval,
            reasonString = reasonString,
        )
    }

    private suspend fun inspectConnack(connack: Connack): Connack {
        connackFlow.emit(connack)

        if (!connack.isSuccess) {
            Logger.i { "Server sent CONNACK packet with ${connack.reason}, hence terminating the connection" }
            connection.disconnect()
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
                        connection.send(Pingreq)
                    }
                }
            }

            // MQTT-3.2.2-16
            if (config.clientId.isEmpty()) {
                connack.assignedClientIdentifier?.let { _clientId = it.value }
            }

            Logger.i {
                "Received server parameters: " +
                        "maxQoS=$maxQos, " +
                        "keepAlive=$keepAlive, " +
                        "serverTopicAliasMaximum=${serverTopicAliasMaximum.value}, " +
                        "assignedClientIdentifier=${connack.assignedClientIdentifier?.value ?: "''"}"
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
            connection.disconnect()
        }
    }

    private suspend fun handlePacket(packet: Packet) {
        Logger.v { "Received new packet: $packet" }
        when (packet) {
            is Disconnect -> {
                Logger.i { "Received DISCONNECT (${packet.reasonString.ifNull(packet.reason)}) from server, disconnecting..." }
                connection.disconnect()
            }

            is Publish -> {
                when (packet.qoS) {
                    QoS.AT_MOST_ONCE -> {
                        _publishedPackets.emit(packet)
                    }

                    QoS.AT_LEAST_ONCE -> {
                        _publishedPackets.emit(packet)
                        connection.send(Puback.from(packet))
                    }

                    QoS.EXACTLY_ONE -> {
                        val id = packet.packetIdentifier!!
                        if (publishReceivedPackets.containsKey(id)) {
                            // Must resend the PUBREC packet
                            publishReceivedPackets[id]?.let {
                                connection.send(it)
                            }
                        } else {
                            _publishedPackets.emit(packet)
                            val pubrec = Pubrec.from(packet)
                            publishReceivedPackets[id] = pubrec
                            connection.send(pubrec)
                        }
                    }
                }
            }

            is Pubrel -> {
                connection.send(Pubcomp.from(packet))
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

public fun MqttClient(host: String, port: Int = 1883, init: MqttClientConfigBuilder.() -> Unit): MqttClient {
    return MqttClient(MqttClientConfigBuilder(host, port).also(init).build())
}

