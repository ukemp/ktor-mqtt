package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.PacketType

public abstract class AbstractPacket(final override val type: PacketType): Packet