package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.util.writeVariableByteInt
import io.ktor.utils.io.core.*

public interface Packet {

    public val type: PacketType
}


internal fun BytePacketBuilder.writeFixedHeader(packet: Packet, remainingLength: Int) {
    writeByte(packet.type.headerByte)
    writeVariableByteInt(remainingLength)
}