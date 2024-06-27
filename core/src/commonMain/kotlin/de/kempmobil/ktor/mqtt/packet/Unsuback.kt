package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*

internal data class Unsuback(
    val packetIdentifier: UShort,
    val reasons: List<ReasonCode>,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY
) : AbstractPacket(PacketType.UNSUBACK) {

    init {
        wellFormedWhen(reasons.isNotEmpty()) { "Reason codes must not be empty in UNSUBACK" }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun BytePacketBuilder.write(unsuback: Unsuback) {
    with(unsuback) {
        writeUShort(packetIdentifier)
        writeProperties(reasonString, *userProperties.asArray)

        // Payload
        reasons.forEach {
            writeByte(it.code.toByte())
        }
    }
}


@OptIn(ExperimentalUnsignedTypes::class)
internal fun ByteReadPacket.readUnsuback(): Unsuback {
    val packetIdentifier = readUShort()
    val properties = readProperties()
    val reasons = buildList {
        while (canRead()) {
            add(ReasonCode.from(readByte()))
        }
    }

    return Unsuback(
        packetIdentifier = packetIdentifier,
        reasonString = properties.singleOrNull<ReasonString>(),
        userProperties = UserProperties.from(properties),
        reasons = reasons
    )
}
