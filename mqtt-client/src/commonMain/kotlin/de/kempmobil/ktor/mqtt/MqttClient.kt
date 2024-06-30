package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.*
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString


public class MqttClient(private val config: MqttClientConfig) {

    private val connection = MqttConnection(config, Receiver())

    private var packetIdentifier: UShort = 1u

    private val isCleanStart: Boolean
        get() = true // TODO

    public fun start() {
        connection.start()
        connection.send(createConnect())
    }

    public fun stop() {
        connection.stop()
    }

    public fun subscribe(
        filters: List<TopicFilter>,
        subscriptionIdentifier: SubscriptionIdentifier?,
        userProperties: UserProperties = UserProperties.EMPTY,
    ) {

    }

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

    private fun nextPacketIdentifier(): UShort {
        packetIdentifier = (packetIdentifier + 1u).toUShort()
        if (packetIdentifier == 0u.toUShort()) {
            packetIdentifier = 1u
        }
        return packetIdentifier
    }

    // ---- Inner classes ----------------------------------------------------------------------------------------------

    private inner class Receiver : PacketReceiver {

        override fun onConnect(connect: Connect) {
            Logger.w { "Illegal client packet received: $connect" }
        }

        override fun onConnack(connack: Connack) {
            Logger.v { "New packet received: $connack" }
            val publish = Publish(
                topicName = "test-topic",
                payload = ByteString("testpayload".toByteArray())
            )
            val subscribe = Subscribe(
                1u,
                listOf(TopicFilter(Topic("test/topic"))),
                subscriptionIdentifier = null
            )
            connection.send(subscribe)
        }

        override fun onPublish(publish: Publish) {
            Logger.v { "New packet received: $publish" }
        }

        override fun onPuback(puback: Puback) {
            Logger.v { "New packet received: $puback" }
        }

        override fun onPubrec(pubrec: Pubrec) {
            Logger.v { "New packet received: $pubrec" }
        }

        override fun onPubrel(pubrel: Pubrel) {
            Logger.v { "New packet received: $pubrel" }
        }

        override fun onPubcomp(pubcomp: Pubcomp) {
            Logger.v { "New packet received: $pubcomp" }
        }

        override fun onSubscribe(subscribe: Subscribe) {
            Logger.w { "Illegal client packet received: $subscribe" }
        }

        override fun onSuback(suback: Suback) {
            Logger.v { "New packet received: $suback" }
        }

        override fun onUnsubscribe(unsubscribe: Unsubscribe) {
            Logger.w { "Illegal client packet received: $unsubscribe" }
        }

        override fun onUnsuback(unsuback: Unsuback) {
            Logger.v { "New packet received: $unsuback" }
        }

        override fun onPingreq() {
            Logger.w { "Illegal client packet received: PINGREQ" }
        }

        override fun onPingresp() {
            Logger.v { "New packet received: PINGRESP" }
        }

        override fun onDisconnect(disconnect: Disconnect) {
            Logger.v { "New packet received: $disconnect" }
        }

        override fun onAuth(auth: Auth) {
            Logger.v { "New packet received: $auth" }
        }

        override fun onMalformedPacket(exception: MalformedPacketException) {
            Logger.e(throwable = exception) { "Malformed packet received" }
        }
    }
}

public fun MqttClient(host: String, port: Int = 1883, init: MqttClientConfigBuilder.() -> Unit): MqttClient {
    return MqttClient(MqttClientConfigBuilder(host, port).also(init).build())
}