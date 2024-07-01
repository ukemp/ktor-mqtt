package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.*
import kotlinx.coroutines.flow.first


public class MqttClient(private val config: MqttClientConfig) {

    private val connection = MqttConnection(config)

    private var packetIdentifier: UShort = 1u

    private var _maxQos = QoS.EXACTLY_ONE
    public val maxQos: QoS
        get() = _maxQos

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
        connection.send(createConnect())

        val connack = (connection.packetsReceived.first { it is Connack }) as Connack
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
            Logger.i { "${config.host} sent maximum Qos: $_maxQos!" }
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