package de.kempmobil.ktor.mqtt.packet

/**
 * Base class for packets that strictly require a packet identifier. Hence, for: PUBACK, PUBREC, PUBREL, PUBCOMP,
 * SUBSCRIBE, SUBACK, UNSUBSCRIBE and UNSUBACK. (Does not include PUBLISH, as here the packet identifier is not always
 * required.)
 */
public abstract class PacketIdentifierPacket(type: PacketType) : AbstractPacket(type) {

    public abstract val packetIdentifier: UShort

    public inline fun <reified T : PacketIdentifierPacket> isResponse(packet: Packet): Boolean {
        return T::class.isInstance(packet) && packetIdentifier == (packet as PacketIdentifierPacket).packetIdentifier
    }
}

