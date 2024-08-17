package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readUShort
import kotlinx.io.writeUShort

/**
 * Base class for PUBACK, PUBREC, PUBREL and PUBCOMP.
 */
public sealed class PublishResponse(
    type: PacketType,
    public override val packetIdentifier: UShort,
    public val reason: ReasonCode,
    public val reasonString: ReasonString? = null,
    public val userProperties: UserProperties = UserProperties.EMPTY
) : AbstractPacket(type), PacketIdentifierPacket {

    override fun toString(): String {
        return "${this::class.simpleName}(packetIdentifier=$packetIdentifier, reason=$reason, reasonString=$reasonString, userProperties=$userProperties)"
    }
}

public class Puback(
    packetIdentifier: UShort,
    reason: ReasonCode,
    reasonString: ReasonString? = null,
    userProperties: UserProperties = UserProperties.EMPTY
) : PublishResponse(PacketType.PUBACK, packetIdentifier, reason, reasonString, userProperties) {

    public companion object {

        public fun from(publish: Publish, reason: ReasonCode = Success, reasonString: String? = null): Puback {
            val packetIdentifier = publish.packetIdentifier
            require(packetIdentifier != null) { "Cannot create a PUBACK packet from a PUBLISH packet without packet identifier: $this" }

            return Puback(
                packetIdentifier = packetIdentifier,
                reason = reason,
                reasonString = reasonString?.let { ReasonString(it) },
                userProperties = publish.userProperties
            )
        }
    }

}

public class Pubrec(
    packetIdentifier: UShort,
    reason: ReasonCode,
    reasonString: ReasonString? = null,
    userProperties: UserProperties = UserProperties.EMPTY
) : PublishResponse(PacketType.PUBREC, packetIdentifier, reason, reasonString, userProperties) {

    public companion object {

        public fun from(publish: Publish, reason: ReasonCode = Success, reasonString: String? = null): Pubrec {
            val packetIdentifier = publish.packetIdentifier
            require(packetIdentifier != null) { "Cannot create a PUBREC packet from a PUBLISH packet without packet identifier: $this" }

            return Pubrec(
                packetIdentifier = packetIdentifier,
                reason = reason,
                reasonString = reasonString?.let { ReasonString(it) },
                userProperties = publish.userProperties
            )
        }
    }
}

public class Pubrel(
    packetIdentifier: UShort,
    reason: ReasonCode,
    reasonString: ReasonString? = null,
    userProperties: UserProperties = UserProperties.EMPTY
) : PublishResponse(PacketType.PUBREL, packetIdentifier, reason, reasonString, userProperties) {

    // Note: this is the only response packet with a header flag!
    override val headerFlags: Int = 2

    public companion object {

        public fun from(publish: Publish, reason: ReasonCode = Success, reasonString: String? = null): Pubrel {
            val packetIdentifier = publish.packetIdentifier
            require(packetIdentifier != null) { "Cannot create a PUBREL packet from a PUBLISH packet without packet identifier: $this" }

            return Pubrel(
                packetIdentifier = packetIdentifier,
                reason = reason,
                reasonString = reasonString?.let { ReasonString(it) },
                userProperties = publish.userProperties
            )
        }
    }
}

public class Pubcomp(
    packetIdentifier: UShort,
    reason: ReasonCode,
    reasonString: ReasonString? = null,
    userProperties: UserProperties = UserProperties.EMPTY
) : PublishResponse(PacketType.PUBCOMP, packetIdentifier, reason, reasonString, userProperties) {

    public companion object {

        public fun from(publish: Publish, reason: ReasonCode = Success, reasonString: String? = null): Pubcomp {
            val packetIdentifier = publish.packetIdentifier
            require(packetIdentifier != null) { "Cannot create a PUBCOMP packet from a PUBLISH packet without packet identifier: $this" }

            return Pubcomp(
                packetIdentifier = packetIdentifier,
                reason = reason,
                reasonString = reasonString?.let { ReasonString(it) },
                userProperties = publish.userProperties
            )
        }

        public fun from(publish: PublishResponse, reason: ReasonCode = Success, reasonString: String? = null): Pubcomp {
            val packetIdentifier = publish.packetIdentifier

            return Pubcomp(
                packetIdentifier = packetIdentifier,
                reason = reason,
                reasonString = reasonString?.let { ReasonString(it) },
                userProperties = publish.userProperties
            )
        }
    }
}

internal interface PublishResponseFactory<T : PublishResponse> {

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

internal fun Sink.write(publishResponse: PublishResponse) {
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

internal fun <T : PublishResponse> Source.readPublishResponse(createResponse: PublishResponseFactory<T>): T {
    val packetIdentifier = readUShort()

    return if (!exhausted()) {
        val reason = ReasonCode.from(readByte())
        val properties = if (!exhausted()) {
            readProperties()
        } else {
            emptyList()
        }
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
