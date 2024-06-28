package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*

public data class Suback(
    val reasons: List<ReasonCode>,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
) : AbstractPacket(PacketType.SUBACK) {

    init {
        wellFormedWhen(reasons.isNotEmpty()) { "Reason codes must not be empty in SUBACK" }
    }
}

internal fun BytePacketBuilder.write(suback: Suback) {
    with(suback) {
        writeProperties(reasonString, *userProperties.asArray)

        // Payload
        reasons.forEach {
            writeByte(it.code.toByte())
        }
    }
}

internal fun ByteReadPacket.readSuback(): Suback {
    val properties = readProperties()
    val reasons = buildList {
        while (canRead()) {
            add(ReasonCode.from(readByte(), defaultSuccessReason = GrantedQoS0))
        }
    }

    return Suback(
        reasonString = properties.singleOrNull<ReasonString>(),
        userProperties = UserProperties.from(properties),
        reasons = reasons
    )
}