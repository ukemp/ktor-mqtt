package de.kempmobil.ktor.mqtt.packet

public object Pingreq : AbstractPacket(PacketType.PINGREQ) {

    override fun toString(): String {
        return "Pingreq"
    }
}

// PINGREQ consists only of the fixed header, hence, nothing else to do here!