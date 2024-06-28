package de.kempmobil.ktor.mqtt.packet

public object Pingresp : AbstractPacket(PacketType.PINGRESP)

// PINGRESP consists only of the fixed header, hence, nothing else to do here!