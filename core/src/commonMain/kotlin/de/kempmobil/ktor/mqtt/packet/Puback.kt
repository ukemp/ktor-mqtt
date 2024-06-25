package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*

public data class Puback(
    public val packetIdentifier: UShort,
    public val reason: ReasonCode,
    public val reasonString: ReasonString? = null,
    public val userProperties: UserProperties = UserProperties.EMPTY
)

@OptIn(ExperimentalUnsignedTypes::class)
internal fun BytePacketBuilder.write(puback: Puback) {
    with(puback) {
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
internal fun ByteReadPacket.readPuback(): Puback {
    val packetIdentifier = readUShort()

    return if (canRead()) {
        val reason = ReasonCode.from(readByte())
        val properties = readProperties()
        Puback(
            packetIdentifier = packetIdentifier,
            reason = reason,
            reasonString = properties.singleOrNull<ReasonString>(),
            userProperties = UserProperties.from(properties)
        )
    } else {
        Puback(packetIdentifier, Success)
    }
}
