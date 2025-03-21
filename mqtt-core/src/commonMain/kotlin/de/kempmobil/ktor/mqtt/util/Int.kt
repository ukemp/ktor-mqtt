package de.kempmobil.ktor.mqtt.util

import de.kempmobil.ktor.mqtt.MalformedPacketException
import io.ktor.utils.io.*
import kotlinx.io.Sink
import kotlinx.io.Source

internal fun Sink.writeVariableByteInt(value: Int) {
    var x = value
    do {
        var encodedByte = x.rem(128)
        x /= 128
        if (x > 0) {
            encodedByte = encodedByte or 128
        }
        writeByte(encodedByte.toByte())
    } while (x > 0)
}

internal suspend fun ByteWriteChannel.writeVariableByteInt(value: Int) {
    var x = value
    do {
        var encodedByte = x.rem(128)
        x /= 128
        if (x > 0) {
            encodedByte = encodedByte or 128
        }
        writeByte(encodedByte.toByte())
    } while (x > 0)
}

internal fun Source.readVariableByteInt(): Int {
    var multiplier = 1
    var value = 0
    do {
        val encodedByte = readByte().toInt()
        value += (encodedByte and 127) * multiplier
        if (multiplier > 128 * 128 * 128) {
            throw MalformedPacketException("malformed variable byte integer")
        }
        multiplier *= 128
    } while ((encodedByte and 128) != 0)

    return value
}

internal suspend fun ByteReadChannel.readVariableByteInt(): Int {
    var multiplier = 1
    var value = 0
    do {
        val encodedByte = readByte().toInt()
        value += (encodedByte and 127) * multiplier
        if (multiplier > 128 * 128 * 128) {
            throw MalformedPacketException("malformed variable byte integer")
        }
        multiplier *= 128
    } while ((encodedByte and 128) != 0)

    return value
}

internal fun Int.variableByteIntSize(): Int {
    var x = this
    var count = 0
    do {
        x /= 128
        count++
    } while (x > 0)

    return count
}
