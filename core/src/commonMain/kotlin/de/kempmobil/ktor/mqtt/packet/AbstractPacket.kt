package de.kempmobil.ktor.mqtt.packet

internal abstract class AbstractPacket(final override val type: PacketType) : Packet {

    override val headerFlags: Int = 0
}
