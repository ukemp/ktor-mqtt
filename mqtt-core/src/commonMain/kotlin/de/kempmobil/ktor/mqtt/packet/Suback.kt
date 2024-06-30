package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*

public data class Suback(
    val packetIdentifier: UShort,
    val reasons: List<ReasonCode>,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
) : AbstractPacket(PacketType.SUBACK) {

    init {
        wellFormedWhen(reasons.isNotEmpty()) { "Reason codes must not be empty in SUBACK" }
    }
}

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