package de.kempmobil.ktor.mqtt.packet

public object Pingresp : AbstractPacket(PacketType.PINGRESP) {

    override fun toString(): String {
        return "Pingresp"
    }
}

// PINGRESP consists only of the fixed header, hence, nothing else to do here!