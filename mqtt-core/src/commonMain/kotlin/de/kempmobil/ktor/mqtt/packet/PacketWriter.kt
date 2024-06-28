package de.kempmobil.ktor.mqtt.packet

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

public suspend fun ByteWriteChannel.write(packet: Packet) {
    val bytes = buildPacket {
        when (packet.type) {
            PacketType.CONNACK -> write(packet as Connack)
            PacketType.CONNECT -> write(packet as Connect)
            PacketType.PUBLISH -> write(packet as Publish)
            PacketType.PUBACK -> write(packet as Puback)
            PacketType.PUBREC -> write(packet as Pubrec)
            PacketType.PUBREL -> write(packet as Pubrel)
            PacketType.PUBCOMP -> write(packet as Pubcomp)
            PacketType.SUBSCRIBE -> write(packet as Subscribe)
            PacketType.SUBACK -> write(packet as Suback)
            PacketType.UNSUBSCRIBE -> write(packet as Unsubscribe)
            PacketType.UNSUBACK -> write(packet as Unsuback)
            PacketType.PINGREQ -> Unit
            PacketType.PINGRESP -> Unit
            PacketType.DISCONNECT -> write(packet as Disconnect)
            PacketType.AUTH -> write(packet as Auth)
        }
    }
    writeFixedHeader(packet, bytes.remaining.toInt())
    writePacket(bytes)
}