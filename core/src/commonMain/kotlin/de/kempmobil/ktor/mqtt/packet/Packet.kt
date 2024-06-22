package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.PacketType

public interface Packet {

    public val type: PacketType
}