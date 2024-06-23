package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.util.writeVariableByteInt
import io.ktor.utils.io.core.*

public abstract class AbstractPacket(final override val type: PacketType): Packet

internal fun BytePacketBuilder.writeFixedHeader(packet: AbstractPacket, remainingLength: Int) {
    writeByte(packet.type.headerByte)
    writeVariableByteInt(remainingLength)
}