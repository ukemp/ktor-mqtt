package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds


public class MqttClient(private val config: MqttClientConfig) {

    public val connectionState: StateFlow<ConnectionState>
        get() = connection.connectionState

    private val connection = MqttConnection(config)

    private val scope = CoroutineScope(config.dispatcher)

    private var packetIdentifier: UShort = 1u

    private var _maxQos = QoS.EXACTLY_ONE
    public val maxQos: QoS
        get() = _maxQos

    private var _serverTopicAliasMaximum: TopicAliasMaximum? = null
    public val serverTopicAliasMaximum: TopicAliasMaximum?
        get() = _serverTopicAliasMaximum

    private val isCleanStart: Boolean
        get() = true // TODO

    /**
     * Tries to connect to the MQTT server and send a CONNECT message.
     *
     * @throws ConnectionException when a connection cannot be established
     * @return the CONNACK message returned by the server
     */
    public suspend fun connect(): Connack {
        connection.start()

        val connect = createConnect()
        val connack: Connack = awaitResponseOf(PacketType.CONNACK) {
            connection.send(connect)
        }

        return inspectConnack(connack)
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
                connection.packetsReceived.first { publish.isAssociatedPuback(it) }
            }

            QoS.EXACTLY_ONE -> {
                connection.packetsReceived.first { publish.isAssociatedPubrec(it) }
                connection.send(Pubrel(packetIdentifier = publish.packetIdentifier!!, Success))
                connection.packetsReceived.first { publish.isAssociatedPubcomp(it) }
            }
        }
    }

    public suspend fun disconnect(reasonCode: ReasonCode = NormalDisconnection, reasonString: ReasonString? = null) {
        connection.send(createDisconnect(reasonCode, reasonString))
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

    private fun inspectConnack(connack: Connack): Connack {
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
    ): P {
        val waitForResponse = scope.async {
            try {
                withTimeout(config.messageTimeout) {
                    (connection.packetsReceived.first(predicate)) as P
                }
            } catch (ex: CancellationException) {
                disconnect(ProtocolError)
                throw TimeoutException("Didn't receive requested packet within ${config.messageTimeout}")
            }
        }
        request()
        return waitForResponse.await()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <P : Packet> awaitResponseOf(type: PacketType, request: suspend () -> Unit): P {
        val waitForResponse = scope.async {
            try {
                withTimeout(config.messageTimeout) {
                    (connection.packetsReceived.first { it.type == type }) as P
                }
            } catch (ex: CancellationException) {
                disconnect(ProtocolError)
                throw TimeoutException("Didn't receive packet of type $type within ${config.messageTimeout}")
            }
        }
        request()
        return waitForResponse.await()
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