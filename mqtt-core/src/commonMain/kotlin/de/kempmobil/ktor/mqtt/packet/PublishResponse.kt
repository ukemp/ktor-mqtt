package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*

/**
 * Base class for PUBACK, PUBREC, PUBREL and PUBCOMP.
 */
internal sealed class PublishResponse(
    type: PacketType,
    val packetIdentifier: UShort,
    val reason: ReasonCode,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY
) : AbstractPacket(type)

internal class Puback(
    packetIdentifier: UShort,
    reason: ReasonCode,
    reasonString: ReasonString? = null,
    userProperties: UserProperties = UserProperties.EMPTY
) : PublishResponse(PacketType.PUBACK, packetIdentifier, reason, reasonString, userProperties)

internal class Pubrec(
    packetIdentifier: UShort,
    reason: ReasonCode,
    reasonString: ReasonString? = null,
    userProperties: UserProperties = UserProperties.EMPTY
) : PublishResponse(PacketType.PUBACK, packetIdentifier, reason, reasonString, userProperties)

internal class Pubrel(
    packetIdentifier: UShort,
    reason: ReasonCode,
    reasonString: ReasonString? = null,
    userProperties: UserProperties = UserProperties.EMPTY
) : PublishResponse(PacketType.PUBACK, packetIdentifier, reason, reasonString, userProperties)

internal class Pubcomp(
    packetIdentifier: UShort,
    reason: ReasonCode,
    reasonString: ReasonString? = null,
    userProperties: UserProperties = UserProperties.EMPTY
) : PublishResponse(PacketType.PUBACK, packetIdentifier, reason, reasonString, userProperties)

internal interface PublishResponseFactory<T> {

    operator fun invoke(
        packetIdentifier: UShort,
        reason: ReasonCode,
        reasonString: ReasonString? = null,
        userProperties: UserProperties = UserProperties.EMPTY
    ): T
}

internal val PubackFactory = object : PublishResponseFactory<Puback> {
    override fun invoke(
        packetIdentifier: UShort,
        reason: ReasonCode,
        reasonString: ReasonString?,
        userProperties: UserProperties
    ) = Puback(packetIdentifier, reason, reasonString, userProperties)
}

internal val PubrecFactory = object : PublishResponseFactory<Pubrec> {
    override fun invoke(
        packetIdentifier: UShort,
        reason: ReasonCode,
        reasonString: ReasonString?,
        userProperties: UserProperties
    ) = Pubrec(packetIdentifier, reason, reasonString, userProperties)
}

internal val PubrelFactory = object : PublishResponseFactory<Pubrel> {
    override fun invoke(
        packetIdentifier: UShort,
        reason: ReasonCode,
        reasonString: ReasonString?,
        userProperties: UserProperties
    ) = Pubrel(packetIdentifier, reason, reasonString, userProperties)
}

internal val PubcompFactory = object : PublishResponseFactory<Pubcomp> {
    override fun invoke(
        packetIdentifier: UShort,
        reason: ReasonCode,
        reasonString: ReasonString?,
        userProperties: UserProperties
    ) = Pubcomp(packetIdentifier, reason, reasonString, userProperties)
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun BytePacketBuilder.write(publishResponse: PublishResponse) {
    with(publishResponse) {
        writeUShort(packetIdentifier)
        if (reason != Success) {
            writeByte(reason.code.toByte())
            writeProperties(
                reasonString,
                *userProperties.asArray
            )
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun <T> ByteReadPacket.readPublishResponse(createResponse: PublishResponseFactory<T>): T {
    val packetIdentifier = readUShort()

    return if (canRead()) {
        val reason = ReasonCode.from(readByte())
        val properties = readProperties()
        createResponse(
            packetIdentifier = packetIdentifier,
            reason = reason,
            reasonString = properties.singleOrNull<ReasonString>(),
            userProperties = UserProperties.from(properties)
        )
    } else {
        createResponse(packetIdentifier, Success)
    }
}
