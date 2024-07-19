package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds


public class MqttClient(private val config: MqttClientConfig) {

    private var _maxQos = QoS.EXACTLY_ONE
    public val maxQos: QoS
        get() = _maxQos

    private var _serverTopicAliasMaximum: TopicAliasMaximum? = null
    public val serverTopicAliasMaximum: TopicAliasMaximum?
        get() = _serverTopicAliasMaximum

    private val connackSuccess = MutableStateFlow(false)

//    public val connectionState: Flow<ConnectionState>
//        get() = connection.state.combine(connackSuccess) { state: ConnectionState, success: Boolean ->
//            when (state) {
//                ConnectionState.CONNECTED -> {
//                    if (success) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
//                }
//
//                ConnectionState.CONNECTING -> ConnectionState.CONNECTING
//                ConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
//            }
//        }

    private val connection = MqttConnection(config)

    private val scope = CoroutineScope(config.dispatcher)

    private val receivedPackets = MutableSharedFlow<Packet>()

    private val _publishedPackets = MutableSharedFlow<Publish>()
    public val publishedPackets: SharedFlow<Publish>
        get() = _publishedPackets

    private var packetIdentifier: UShort = 1u

    private val isCleanStart: Boolean
        get() = true // TODO

    init {
        scope.launch {
            connection.packetResults.collect { result ->
                result.onSuccess {
                    receivedPackets.emit(it)
                    if (it is Publish) {
                        _publishedPackets.emit(it)
                    }
                }.onFailure {
                    if (it.cause is MalformedPacketException) {
                        Logger.w { "Received malformed packet: '${it.message}', disconnecting..." }
                    } else {
                        Logger.w(throwable = it) { "Unexpected error while parsing a packet, disconnecting..." }
                    }
                    connection.disconnect()
                }
            }
        }
    }

    /**
     * Tries to connect to the MQTT server and send a CONNECT message.
     *
     * @throws ConnectionException when a connection cannot be established
     * @return the CONNACK message returned by the server. Callers must check the returned [Connack] instance to find
     *         out whether the connection has actually been successfully established, as the server might return a
     *         `Connack` with an error message (see also [Connack.isSuccess]).
     */
    public suspend fun connect(): Result<Connack> {
        return connection.start()
            .map {
                awaitResponseOf<Connack>(PacketType.CONNACK) {
                    connection.send(createConnect())
                }.getOrThrow().also {
                    inspectConnack(it)
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

    public suspend fun publish(publish: Publish) {
        if (publish.qoS.value > maxQos.value) {
            throw MalformedPacketException("QoS of $publish is larger than the server QoS of $maxQos")
        }
        connection.send(publish)

        when (publish.qoS) {
            QoS.AT_MOST_ONCE -> {
                return
            }

            QoS.AT_LEAST_ONCE -> {
                receivedPackets.first { publish.isAssociatedPuback(it) }
            }

            QoS.EXACTLY_ONE -> {
                receivedPackets.first { publish.isAssociatedPubrec(it) }
                connection.send(Pubrel(packetIdentifier = publish.packetIdentifier!!, Success))
                receivedPackets.first { publish.isAssociatedPubcomp(it) }
            }
        }
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
            userName = config.userName,
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

    private suspend fun inspectConnack(connack: Connack): Connack {
        connackSuccess.emit(connack.isSuccess)

        if (!connack.isSuccess) {
            Logger.i { "Server sent CONNACK packet with ${connack.reason}, hence terminating the connection" }
            connection.disconnect()
        } else {
            connack.maximumQoS?.let {
                _maxQos = it.qoS
            }
            _serverTopicAliasMaximum = connack.topicAliasMaximum

            val keepAlive = (connack.serverKeepAlive?.value ?: config.keepAliveSeconds).toInt().seconds
            if (keepAlive.inWholeSeconds > 0) {
                scope.launch {
                    delay(keepAlive)
                    awaitResponseOf<Pingresp>(PacketType.PINGRESP) {
                        connection.send(Pingreq)
                    }
                }
            }

            Logger.i {
                "Received server parameters: max. QoS=$maxQos, " +
                        "keep alive=$keepAlive sec., " +
                        "server topic alias maximum=$serverTopicAliasMaximum"
            }
        }

        return connack
    }

    private fun createDisconnect(reasonCode: ReasonCode, reasonString: ReasonString?): Disconnect {
        return Disconnect(
            reasonCode,
            sessionExpiryInterval = config.sessionExpiryInterval,
            reasonString = reasonString,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <P : Packet> awaitResponseOf(
        predicate: suspend (Packet) -> Boolean,
        request: suspend () -> Unit
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
        request()
        return waitForResponse.await()
    }

    private suspend fun <P : Packet> awaitResponseOf(type: PacketType, request: suspend () -> Unit): Result<P> {
        return awaitResponseOf({ it.type == type }, request)
    }

    private fun nextPacketIdentifier(): UShort {
        packetIdentifier = (packetIdentifier + 1u).toUShort()
        if (packetIdentifier == 0u.toUShort()) {
            packetIdentifier = 1u
        }
        return packetIdentifier
    }
}

public fun MqttClient(host: String, port: Int = 1883, init: MqttClientConfigBuilder.() -> Unit): MqttClient {
    return MqttClient(MqttClientConfigBuilder(host, port).also(init).build())
}