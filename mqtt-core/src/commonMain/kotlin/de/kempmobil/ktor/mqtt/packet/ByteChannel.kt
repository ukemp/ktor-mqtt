package de.kempmobil.ktor.mqtt.packet

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.util.readVariableByteInt
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

public suspend fun ByteReadChannel.readPacket(): Packet {
    val header = readByte()
    val type = PacketType.from(header)
    val length = readVariableByteInt()
    val bytes = readPacket(length)

    Logger.v { "Received new packet of type: $type" }

    return when (type) {
        PacketType.CONNACK -> bytes.readConnack()
        PacketType.CONNECT -> bytes.readConnect()
        PacketType.PUBLISH -> bytes.readPublish(header.toInt())
        PacketType.PUBACK -> bytes.readPublishResponse(PubackFactory)
        PacketType.PUBREC -> bytes.readPublishResponse(PubrecFactory)
        PacketType.PUBREL -> bytes.readPublishResponse(PubrelFactory)
        PacketType.PUBCOMP -> bytes.readPublishResponse(PubcompFactory)
        PacketType.SUBSCRIBE -> bytes.readSubscribe()
        PacketType.SUBACK -> bytes.readSuback()
        PacketType.UNSUBSCRIBE -> bytes.readUnsubscribe()
        PacketType.UNSUBACK -> bytes.readUnsuback()
        PacketType.PINGREQ -> Pingreq
        PacketType.PINGRESP -> Pingresp
        PacketType.DISCONNECT -> bytes.readDisconnect()
        PacketType.AUTH -> bytes.readAuth()
    }
}

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