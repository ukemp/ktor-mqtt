package de.kempmobil.ktor.mqtt.util

import de.kempmobil.ktor.mqtt.MalformedPacketException
import io.ktor.utils.io.core.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.bytestring.ByteString

private const val MAX_BYTES_SIZE = 65_535

/**
 * Writes the size of the byte string and then the byte string as specified in MQTT.
 *
 * @throws MalformedPacketException when the byte string is larger than 65,535 bytes.
 */
internal fun Sink.writeMqttByteString(bytes: ByteString) {
    if (bytes.size > MAX_BYTES_SIZE) {
        throw MalformedPacketException("ByteString is too long: ${bytes.size} (max allowed size: ${MAX_BYTES_SIZE})")
    }

    writeShort(bytes.size.toShort())
    writeFully(bytes.toByteArray())
}

/**
 * Reads a byte string from the specified packet, reading the bytes size first.
 */
internal fun Source.readMqttByteString(): ByteString {
    val bytes = ByteArray(readShort().toInt())
    readFully(bytes)

    return ByteString(bytes)
}
