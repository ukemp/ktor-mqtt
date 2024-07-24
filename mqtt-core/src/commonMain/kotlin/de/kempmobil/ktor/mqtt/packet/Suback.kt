package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*

public data class Suback(
    override val packetIdentifier: UShort,
    val reasons: List<ReasonCode>,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
) : AbstractPacket(PacketType.SUBACK), PacketIdentifierPacket {

    init {
        wellFormedWhen(reasons.isNotEmpty()) { "Reason codes must not be empty in SUBACK" }
    }
}

/**
 * Returns `true` when this SUBACK packet contains a reason code which not indicates a success.
 */
public val Suback.hasFailure: Boolean
    get() = reasons.any { it.code > GrantedQoS2.code }

@OptIn(ExperimentalUnsignedTypes::class)
internal fun BytePacketBuilder.write(suback: Suback) {
    with(suback) {
        writeUShort(packetIdentifier)
        writeProperties(reasonString, *userProperties.asArray)

        // Payload
        reasons.forEach {
            writeByte(it.code.toByte())
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun ByteReadPacket.readSuback(): Suback {
    val packetIdentifier = readUShort()
    val properties = readProperties()
    val reasons = buildList {
        while (canRead()) {
            add(ReasonCode.from(readByte(), defaultSuccessReason = GrantedQoS0))
        }
    }

    return Suback(
        packetIdentifier = packetIdentifier,
        reasonString = properties.singleOrNull<ReasonString>(),
        userProperties = UserProperties.from(properties),
        reasons = reasons
    )
}