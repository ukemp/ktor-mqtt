package de.kempmobil.ktor.mqtt.packet

/**
 * Interface identifying packets that strictly require a packet identifier. Hence, for: PUBACK, PUBREC, PUBREL, PUBCOMP,
 * SUBSCRIBE, SUBACK, UNSUBSCRIBE and UNSUBACK. (Does not include PUBLISH, as the packet identifier is not always
 * required for PUBLISH packets.)
 */
public interface PacketIdentifierPacket : Packet {

    public val packetIdentifier: UShort
}
