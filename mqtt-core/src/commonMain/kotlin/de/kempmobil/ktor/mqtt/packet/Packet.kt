package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.util.writeVariableByteInt
import io.ktor.utils.io.*

public interface Packet {

    public val type: PacketType

    public val headerFlags: Int
}

internal suspend fun ByteWriteChannel.writeFixedHeader(packet: Packet, remainingLength: Int) {
    check(packet.headerFlags < 16) { "Header flags may only contain 4 bits: ${packet.headerFlags}" }
    writeByte(((packet.type.value shl 4) or packet.headerFlags).toByte())
    writeVariableByteInt(remainingLength)
}
