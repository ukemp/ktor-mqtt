package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.MalformedPacketException
import de.kempmobil.ktor.mqtt.packet.PacketDirection.*

// TODO: do we need this?
public enum class PacketType(
    internal val value: Int,
    internal val direction: PacketDirection,
    internal val hasVariableHeader: Boolean,
    internal val mayHavePayload: Boolean
) {
    CONNECT(1, CLIENT_TO_SERVER, true, true),
    CONNACK(2, SERVER_TO_CLIENT, false,false),
    PUBLISH(3, BOTH, true, true),
    PUBACK(4, BOTH, true, false),
    PUBREC(5, BOTH, true, false),
    PUBREL(6, BOTH, true, false),
    PUBCOMP(7, BOTH, true, false),
    SUBSCRIBE(8, CLIENT_TO_SERVER, true, true),
    SUBACK(9, SERVER_TO_CLIENT, true, true),
    UNSUBSCRIBE(10, CLIENT_TO_SERVER,true, true),
    UNSUBACK(11, SERVER_TO_CLIENT, true, true),
    PINGREQ(12, CLIENT_TO_SERVER, false, false),
    PINGRESP(13, SERVER_TO_CLIENT, false, false),
    DISCONNECT(14, BOTH, false, false),
    AUTH(15, BOTH, true, false);

    public companion object {

        /**
         * Converts the upper 4 bits of the specified MQTT header field into an instance of this.
         */
        public fun from(header: Byte): PacketType {
            val value = header.toInt() shr 4
            return entries.firstOrNull { it.value == value }
                ?: throw MalformedPacketException("Unknown header type: $header")
        }
    }
}
