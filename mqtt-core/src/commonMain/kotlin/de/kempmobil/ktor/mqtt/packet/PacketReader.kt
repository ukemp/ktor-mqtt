package de.kempmobil.ktor.mqtt.packet

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.util.readVariableByteInt
import io.ktor.utils.io.*


public suspend fun ByteReadChannel.readPacket(receiver: PacketReceiver) {
    val header = readByte()
    val type = PacketType.from(header)
    val length = readVariableByteInt()
    val bytes = readPacket(length)

    Logger.v { "Received new packet of type: $type" }

    with(receiver) {
        when (type) {
            PacketType.CONNACK -> onConnack(bytes.readConnack())
            PacketType.CONNECT -> onConnect(bytes.readConnect())
            PacketType.PUBLISH -> onPublish(bytes.readPublish(header.toInt()))
            PacketType.PUBACK -> onPuback(bytes.readPublishResponse(PubackFactory))
            PacketType.PUBREC -> onPubrec(bytes.readPublishResponse(PubrecFactory))
            PacketType.PUBREL -> onPubrel(bytes.readPublishResponse(PubrelFactory))
            PacketType.PUBCOMP -> onPubcomp(bytes.readPublishResponse(PubcompFactory))
            PacketType.SUBSCRIBE -> onSubscribe(bytes.readSubscribe())
            PacketType.SUBACK -> onSuback(bytes.readSuback())
            PacketType.UNSUBSCRIBE -> onUnsubscribe(bytes.readUnsubscribe())
            PacketType.UNSUBACK -> onUnsuback(bytes.readUnsuback())
            PacketType.PINGREQ -> onPingreq()
            PacketType.PINGRESP -> onPingresp()
            PacketType.DISCONNECT -> onDisconnect(bytes.readDisconnect())
            PacketType.AUTH -> onAuth(bytes.readAuth())
        }
    }
}