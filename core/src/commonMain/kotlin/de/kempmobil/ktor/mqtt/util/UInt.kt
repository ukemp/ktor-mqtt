package de.kempmobil.ktor.mqtt.util

import de.kempmobil.ktor.mqtt.MalformedPacketException
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

internal fun BytePacketBuilder.writeVariableByteUInt(value: UInt) {
    var x = value
    do {
        var encodedByte = x.rem(128u)
        x /= 128u
        if (x > 0u) {
            encodedByte = encodedByte or 128u
        }
        writeByte(encodedByte.toByte())
    } while (x > 0u)
}

internal fun ByteReadPacket.readVariableByteUInt(): UInt {
    var multiplier = 1u
    var value = 0u
    do {
        val encodedByte = readByte().toUInt()
        value += (encodedByte and 127u) * multiplier
        if (multiplier > 128u * 128u * 128u) {
            throw MalformedPacketException("malformed variable byte integer")
        }
        multiplier *= 128u
    } while ((encodedByte and 128u) != 0u)

    return value
}