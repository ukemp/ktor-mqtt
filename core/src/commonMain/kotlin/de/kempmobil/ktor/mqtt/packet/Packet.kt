package de.kempmobil.ktor.mqtt.packet

internal interface Packet {

    public val type: PacketType
}