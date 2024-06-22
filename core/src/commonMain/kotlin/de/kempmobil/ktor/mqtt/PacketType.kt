package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.PacketDirection.CLIENT_TO_SERVER
import de.kempmobil.ktor.mqtt.PacketDirection.SERVER_TO_CLIENT
import de.kempmobil.ktor.mqtt.PacketDirection.BOTH

public enum class PacketType(
    public val value: Int,
    public val direction: PacketDirection,
    public val hasVariableHeader: Boolean,
    public val mayHavePayload: Boolean
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
