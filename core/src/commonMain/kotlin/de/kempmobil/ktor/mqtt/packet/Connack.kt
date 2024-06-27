package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*

internal data class Connack(
    public val isSessionPresent: Boolean,
    public val reason: ReasonCode,
    public val sessionExpiryInterval: SessionExpiryInterval? = null,
    public val receiveMaximum: ReceiveMaximum? = null,
    public val maximumQoS: MaximumQoS? = null,
    public val retainAvailable: RetainAvailable? = null,
    public val maximumPacketSize: MaximumPacketSize? = null,
    public val assignedClientIdentifier: AssignedClientIdentifier? = null,
    public val topicAliasMaximum: TopicAliasMaximum? = null,
    public val reasonString: ReasonString? = null,
    public val userProperties: UserProperties = UserProperties.EMPTY,
    public val wildcardSubscriptionAvailable: WildcardSubscriptionAvailable? = null,
    public val subscriptionIdentifierAvailable: SubscriptionIdentifierAvailable? = null,
    public val sharedSubscriptionAvailable: SharedSubscriptionAvailable? = null,
    public val serverKeepAlive: ServerKeepAlive? = null,
    public val responseInformation: ResponseInformation? = null,
    public val serverReference: ServerReference? = null,
    public val authenticationMethod: AuthenticationMethod? = null,
    public val authenticationData: AuthenticationData? = null
) : AbstractPacket(PacketType.CONNACK)

internal fun BytePacketBuilder.write(connack: Connack) {
    with(connack) {
        writeByte(if (isSessionPresent) 1 else 0)
        writeByte(reason.code.toByte())
        writeProperties(
            sessionExpiryInterval,
            receiveMaximum,
            maximumQoS,
            retainAvailable,
            maximumPacketSize,
            assignedClientIdentifier,
            topicAliasMaximum,
            reasonString,
            wildcardSubscriptionAvailable,
            subscriptionIdentifierAvailable,
            sharedSubscriptionAvailable,
            serverKeepAlive,
            responseInformation,
            serverReference,
            authenticationMethod,
            authenticationData,
            *userProperties.asArray
        )
    }
}

/**
 * Constructs a Connack packet from this byte read packet. Expects the packet to start at the remaining length (byte 2)
 * of the fixed header of the Connack packet.
 */
internal fun ByteReadPacket.readConnack(): Connack {
    val isSessionPresent = readByte() == 1.toByte()
    val reason = ReasonCode.from(readByte())
    val properties = readProperties()

    return Connack(
        isSessionPresent = isSessionPresent,
        reason = reason,
        sessionExpiryInterval = properties.singleOrNull<SessionExpiryInterval>(),
        receiveMaximum = properties.singleOrNull<ReceiveMaximum>(),
        maximumQoS = properties.singleOrNull<MaximumQoS>(),
        retainAvailable = properties.singleOrNull<RetainAvailable>(),
        maximumPacketSize = properties.singleOrNull<MaximumPacketSize>(),
        assignedClientIdentifier = properties.singleOrNull<AssignedClientIdentifier>(),
        topicAliasMaximum = properties.singleOrNull<TopicAliasMaximum>(),
        reasonString = properties.singleOrNull<ReasonString>(),
        userProperties = UserProperties.from(properties),
        wildcardSubscriptionAvailable = properties.singleOrNull<WildcardSubscriptionAvailable>(),
        subscriptionIdentifierAvailable = properties.singleOrNull<SubscriptionIdentifierAvailable>(),
        sharedSubscriptionAvailable = properties.singleOrNull<SharedSubscriptionAvailable>(),
        serverKeepAlive = properties.singleOrNull<ServerKeepAlive>(),
        responseInformation = properties.singleOrNull<ResponseInformation>(),
        serverReference = properties.singleOrNull<ServerReference>(),
        authenticationMethod = properties.singleOrNull<AuthenticationMethod>(),
        authenticationData = properties.singleOrNull<AuthenticationData>(),
    )
}
