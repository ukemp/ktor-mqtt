package de.kempmobil.ktor.mqtt.packet

internal object Pingreq : AbstractPacket(PacketType.PINGREQ)

// PINGREQ consists only of the fixed header, hence, nothing else to do here!