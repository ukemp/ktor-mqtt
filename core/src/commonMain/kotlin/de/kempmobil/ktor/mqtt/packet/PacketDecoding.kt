package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.util.readVariableByteInt
import io.ktor.utils.io.*

internal suspend fun ByteReadChannel.readPacket(): Packet {
    val header = readByte()
    val type = PacketType.from(header)
    val length = readVariableByteInt()
    val bytes = readPacket(length)

    return when (type) {
        PacketType.CONNACK -> bytes.readConnack()
        PacketType.CONNECT -> bytes.readConnack()
        PacketType.PUBLISH -> bytes.readPublish(header.toInt())
        PacketType.PUBACK -> bytes.readPublishResponse(PubackFactory)
        PacketType.PUBREC -> bytes.readPublishResponse(PubrecFactory)
        PacketType.PUBREL -> bytes.readPublishResponse(PubrelFactory)
        PacketType.PUBCOMP -> bytes.readPublishResponse(PubcompFactory)
        PacketType.SUBSCRIBE -> bytes.readSubscribe()
        PacketType.SUBACK -> bytes.readSuback()
        PacketType.UNSUBSCRIBE -> bytes.readUnsubscribe()
        PacketType.UNSUBACK -> bytes.readUnsuback()
        PacketType.DISCONNECT -> bytes.readDisconnect()
        PacketType.AUTH -> bytes.readAuth()
        PacketType.PINGREQ -> Pingreq
        PacketType.PINGRESP -> Pingresp
    }
}