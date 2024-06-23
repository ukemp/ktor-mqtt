package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.writeMqttByteString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import de.kempmobil.ktor.mqtt.util.writeVariableByteInt
import io.ktor.utils.io.core.*

public class Connect(
    public val isCleanStart: Boolean,
    public val willMessage: WillMessage?,
    public val willOqS: QoS,
    public val retainWillMessage: Boolean,
    public val keepAliveSeconds: UShort,
    public val clientId: String,
    public val userName: String? = null,
    public val password: String? = null,
    public val sessionExpiryInterval: SessionExpiryInterval? = null,
    public val receiveMaximum: ReceiveMaximum? = null,
    public val maximumPacketSize: MaximumPacketSize? = null,
    public val topicAliasMaximum: TopicAliasMaximum? = null,
    public val requestResponseInformation: RequestResponseInformation? = null,
    public val requestProblemInformation: RequestProblemInformation? = null,
    public val userProperties: UserProperties = UserProperties.EMPTY,
    public val authenticationMethod: AuthenticationMethod? = null,
    public val authenticationData: AuthenticationData? = null
) : AbstractPacket(PacketType.CONNECT)

// The MQTT protocol name: "04MQTT" encoded as an MQTT string
private val ProtocolName = byteArrayOf(0, 4, 77, 81, 84, 84)

internal fun BytePacketBuilder.write(connect: Connect) {
    writeFully(ProtocolName)
    writeByte(5) // MQTT version 5

    with(connect) {
        writeByte(bits)
        writeShort(keepAliveSeconds.toShort())

        // Write properties
        writeVariableByteInt(propertiesByteCount)
        if (sessionExpiryInterval != null) write(sessionExpiryInterval)
        if (receiveMaximum != null) write(receiveMaximum)
        if (maximumPacketSize != null) write(maximumPacketSize)
        if (topicAliasMaximum != null) write(topicAliasMaximum)
        if (requestResponseInformation != null) write(requestResponseInformation)
        if (requestProblemInformation != null) write(requestProblemInformation)
        write(userProperties)
        if (authenticationMethod != null) write(authenticationMethod)
        if (authenticationData != null) write(authenticationData)

        // Write the payload
        writeMqttString(clientId) // Must always be present!
        if (willMessage != null) {
            write(willMessage.properties)
            writeMqttString(willMessage.topic)
            writeMqttByteString(willMessage.payload)
        }
        if (userName != null) {
            writeMqttString(userName)
        }
        if (password != null) {
            writeMqttString(password)
        }
    }
}

private val Connect.propertiesByteCount: Int
    get() = sessionExpiryInterval.byteCount + receiveMaximum.byteCount + maximumPacketSize.byteCount + topicAliasMaximum.byteCount +
            requestResponseInformation.byteCount + requestProblemInformation.byteCount + userProperties.byteCount() +
            authenticationMethod.byteCount + authenticationData.byteCount

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
