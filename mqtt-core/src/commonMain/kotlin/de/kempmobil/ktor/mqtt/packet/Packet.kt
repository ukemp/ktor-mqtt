package de.kempmobil.ktor.mqtt.packet

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.util.readVariableByteInt
import de.kempmobil.ktor.mqtt.util.writeVariableByteInt
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.Sink
import kotlinx.io.Source

public interface Packet {

    public val type: PacketType

    public val headerFlags: Int
}

/**
 * Determines whether this packet is of the specified type and its packet identifier is the same as the one of `packet`.
 */
public inline fun <reified T : PacketIdentifierPacket> Packet.isResponseFor(packet: PacketIdentifierPacket): Boolean {
    return T::class.isInstance(this) && packet.packetIdentifier == (this as PacketIdentifierPacket).packetIdentifier
}

/**
 * Determines whether this packet is of the specified type and its packet identifier is the same as the one of `publish`.
 */
public inline fun <reified T : PacketIdentifierPacket> Packet.isResponseFor(publish: Publish): Boolean {
    return T::class.isInstance(this) && publish.packetIdentifier == (this as PacketIdentifierPacket).packetIdentifier
}

/**
 * Reads a packet from this byte read channel. Blocks until the packet has been read completely
 *
 * @throws MalformedPacketException when the packet cannot be parsed
 */
public suspend fun ByteReadChannel.readPacket(): Packet {
    val header = readByte()
    val type = PacketType.from(header)
    val length = readVariableByteInt()
    val bytes = readPacket(length)

    Logger.v { "Received new packet of type: $type" }
    return bytes.readBody(type, header)
}

// TODO: remove this, only use ByteReadChannel.readPacket()
public fun Source.readPacket(): Packet {
    val header = readByte()
    val type = PacketType.from(header)
    val length = readVariableByteInt()
    require(length.toLong())

    Logger.v { "Received new packet of type: $type" }
    return readBody(type, header)
}

private fun Source.readBody(type: PacketType, headerFlags: Byte): Packet {
    return when (type) {
        PacketType.CONNACK -> readConnack()
        PacketType.CONNECT -> readConnect()
        PacketType.PUBLISH -> readPublish(headerFlags.toInt())
        PacketType.PUBACK -> readPublishResponse(PubackFactory)
        PacketType.PUBREC -> readPublishResponse(PubrecFactory)
        PacketType.PUBREL -> readPublishResponse(PubrelFactory)
        PacketType.PUBCOMP -> readPublishResponse(PubcompFactory)
        PacketType.SUBSCRIBE -> readSubscribe()
        PacketType.SUBACK -> readSuback()
        PacketType.UNSUBSCRIBE -> readUnsubscribe()
        PacketType.UNSUBACK -> readUnsuback()
        PacketType.PINGREQ -> Pingreq
        PacketType.PINGRESP -> Pingresp
        PacketType.DISCONNECT -> readDisconnect()
        PacketType.AUTH -> readAuth()
    }
}

/**
 * Writes the bytes of the specified packet to this byte write channel.
 */
public suspend fun ByteWriteChannel.write(packet: Packet) {
    val bytes = buildPacket {
        writeBody(packet)
    }
    writeFixedHeader(packet, bytes.remaining.toInt())
    writePacket(bytes)
}

public fun Sink.write(packet: Packet) {
    val bytes = buildPacket {
        writeBody(packet)
    }
    writeFixedHeader(packet, bytes.remaining.toInt())
    writePacket(bytes)
}

/**
 * Write the bytes of this packet, **excluding** the variable header part.
 */
private fun Sink.writeBody(packet: Packet) {
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

private fun Sink.writeFixedHeader(packet: Packet, remainingLength: Int) {
    check(packet.headerFlags < 16) { "Header flags may only contain 4 bits: ${packet.headerFlags}" }
    writeByte(((packet.type.value shl 4) or packet.headerFlags).toByte())
    writeVariableByteInt(remainingLength)
}

private suspend fun ByteWriteChannel.writeFixedHeader(packet: Packet, remainingLength: Int) {
    check(packet.headerFlags < 16) { "Header flags may only contain 4 bits: ${packet.headerFlags}" }
    writeByte(((packet.type.value shl 4) or packet.headerFlags).toByte())
    writeVariableByteInt(remainingLength)
}
