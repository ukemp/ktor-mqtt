package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.util.writeVariableByteInt
import io.ktor.utils.io.*

public abstract class AbstractPacket(final override val type: PacketType) : Packet {

    internal open val headerFlags: Int = 0
}

internal suspend fun ByteWriteChannel.writeFixedHeader(packet: AbstractPacket, remainingLength: Int) {
    check(packet.headerFlags < 16) { "Header flags may only contain 4 bits: ${packet.headerFlags}" }
    writeByte(((packet.type.value shl 4) or packet.headerFlags).toByte())
    writeVariableByteInt(remainingLength)
}
