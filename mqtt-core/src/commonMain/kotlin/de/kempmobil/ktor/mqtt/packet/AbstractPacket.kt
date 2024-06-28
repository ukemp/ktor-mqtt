package de.kempmobil.ktor.mqtt.packet

public abstract class AbstractPacket(final override val type: PacketType) : Packet {

    override val headerFlags: Int = 0
}
