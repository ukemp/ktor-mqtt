package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readUShort

public data class Connect(
    val isCleanStart: Boolean,
    val willMessage: WillMessage?,
    val willOqS: QoS,
    val retainWillMessage: Boolean,
    val keepAliveSeconds: UShort,
    val clientId: String,
    val userName: String? = null,
    val password: String? = null,
    val sessionExpiryInterval: SessionExpiryInterval? = null,
    val receiveMaximum: ReceiveMaximum? = null,
    val maximumPacketSize: MaximumPacketSize? = null,
    val topicAliasMaximum: TopicAliasMaximum? = null,
    val requestResponseInformation: RequestResponseInformation? = null,
    val requestProblemInformation: RequestProblemInformation? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
    val authenticationMethod: AuthenticationMethod? = null,
    val authenticationData: AuthenticationData? = null
) : AbstractPacket(PacketType.CONNECT) {

    init {
        wellFormedWhen((willMessage != null) || (willOqS == QoS.AT_MOST_ONCE)) {
            "If the Will Flag is set to 0, then the Will QoS MUST be set to 0 (0x00) [MQTT-3.1.2-11]"
        }
        malformedWhen(willMessage == null && retainWillMessage) {
            "If the Will Flag is set to 0, then Will Retain MUST be set to 0 [MQTT-3.1.2-13]"
        }
    }

    override fun toString(): String {
        // Overwritten to prevent printing of password
        return "Connect(isCleanStart=$isCleanStart, willMessage=$willMessage, willOqS=$willOqS, retainWillMessage=$retainWillMessage, " +
                "keepAliveSeconds=$keepAliveSeconds, clientId='$clientId', userName=$userName, password=********, " +
                "sessionExpiryInterval=$sessionExpiryInterval, receiveMaximum=$receiveMaximum, maximumPacketSize=$maximumPacketSize, " +
                "topicAliasMaximum=$topicAliasMaximum, requestResponseInformation=$requestResponseInformation, " +
                "requestProblemInformation=$requestProblemInformation, userProperties=$userProperties, " +
                "authenticationMethod=$authenticationMethod, authenticationData=$authenticationData)"
    }
}

// The MQTT protocol name: "04MQTT" encoded as an MQTT string
private val ProtocolName = byteArrayOf(0, 4, 77, 81, 84, 84)

internal fun Sink.write(connect: Connect) {
    writeFully(ProtocolName)
    writeByte(5) // MQTT version 5

    with(connect) {
        writeByte(bits)
        writeShort(keepAliveSeconds.toShort())
        writeProperties(
            sessionExpiryInterval,
            receiveMaximum,
            maximumPacketSize,
            topicAliasMaximum,
            requestResponseInformation,
            requestProblemInformation,
            authenticationMethod,
            authenticationData,
            *userProperties.asArray
        )

        // Write the payload
        writeMqttString(clientId) // Must always be present!
        if (willMessage != null) {
            write(willMessage)
        }
        if (userName != null) {
            writeMqttString(userName)
        }
        if (password != null) {
            writeMqttString(password)
        }
    }
}

internal fun Source.readConnect(): Connect {
    val protocolName = readMqttString()
    wellFormedWhen(protocolName == "MQTT") { "Connect packet must start with 'MQTT', but is: '$protocolName'" }

    val version = readByte()
    wellFormedWhen(version == 5.toByte()) { "Connect packet version must be 5, but is: $version" }

    val bits = readByte()
    val keepAliveSeconds = readUShort()
    val properties = readProperties()
    val clientId = readMqttString()
    val willMessage = if (bits.isWillMessageFlagSet) {
        readWillMessage()
    } else {
        null
    }
    val userName = if (bits.isUserNameFlagSet) {
        readMqttString()
    } else {
        null
    }
    val password = if (bits.isPasswordFlagSet) {
        readMqttString()
    } else {
        null
    }

    return Connect(
        isCleanStart = bits.isCleanStartFlagSet,
        willMessage = willMessage,
        willOqS = bits.willQoS,
        retainWillMessage = bits.isRetainWillMessageFlagSet,
        keepAliveSeconds = keepAliveSeconds,
        clientId = clientId,
        userName = userName,
        password = password,
        sessionExpiryInterval = properties.singleOrNull<SessionExpiryInterval>(),
        receiveMaximum = properties.singleOrNull(),
        maximumPacketSize = properties.singleOrNull(),
        topicAliasMaximum = properties.singleOrNull(),
        requestResponseInformation = properties.singleOrNull<RequestResponseInformation>(),
        requestProblemInformation = properties.singleOrNull<RequestProblemInformation>(),
        userProperties = UserProperties.from(properties),
        authenticationMethod = properties.singleOrNull<AuthenticationMethod>(),
        authenticationData = properties.singleOrNull<AuthenticationData>()
    )
}

private val Connect.bits: Byte
    get() {
        var bits = if (isCleanStart) 2 else 0
        if (willMessage != null) {
            // When there is not will message, the QoS and retain flags must be zero, hence evaluate them here:
            bits = (bits or 4) or (willOqS.value shl 3)
            if (retainWillMessage) bits = bits or (1 shl 5)
        }
        if (password != null) bits = bits or (1 shl 6)
        if (userName != null) bits = bits or (1 shl 7)

        return bits.toByte()
    }

private val Byte.willQoS: QoS
    get() = QoS.from(((toInt() and 24) shr 3))

private val Byte.isCleanStartFlagSet: Boolean
    get() = (toInt() and 2) != 0

private val Byte.isWillMessageFlagSet: Boolean
    get() = (toInt() and 4) != 0

private val Byte.isRetainWillMessageFlagSet: Boolean
    get() = (toInt() and (1 shl 5)) != 0

private val Byte.isPasswordFlagSet: Boolean
    get() = (toInt() and (1 shl 6)) != 0

private val Byte.isUserNameFlagSet: Boolean
    get() = (toInt() and (1 shl 7)) != 0
