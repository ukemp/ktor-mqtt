package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.io.Sink
import kotlinx.io.Source

public data class Connack(
    val isSessionPresent: Boolean,
    val reason: ReasonCode,
    val sessionExpiryInterval: SessionExpiryInterval? = null,
    val receiveMaximum: ReceiveMaximum? = null,
    val maximumQoS: MaximumQoS? = null,
    val retainAvailable: RetainAvailable? = null,
    val maximumPacketSize: MaximumPacketSize? = null,
    val assignedClientIdentifier: AssignedClientIdentifier? = null,
    val topicAliasMaximum: TopicAliasMaximum? = null,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
    val wildcardSubscriptionAvailable: WildcardSubscriptionAvailable? = null,
    val subscriptionIdentifierAvailable: SubscriptionIdentifierAvailable? = null,
    val sharedSubscriptionAvailable: SharedSubscriptionAvailable? = null,
    val serverKeepAlive: ServerKeepAlive? = null,
    val responseInformation: ResponseInformation? = null,
    val serverReference: ServerReference? = null,
    val authenticationMethod: AuthenticationMethod? = null,
    val authenticationData: AuthenticationData? = null
) : AbstractPacket(PacketType.CONNACK) {

    /**
     * Determines whether this CONNACK represents a successful connection attempt.
     */
    public val isSuccess: Boolean
        get() = reason.code == 0
}

internal fun Sink.write(connack: Connack) {
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
internal fun Source.readConnack(): Connack {
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
