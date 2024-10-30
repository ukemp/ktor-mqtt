package de.kempmobil.ktor.mqtt.util

import de.kempmobil.ktor.mqtt.*
import kotlinx.io.*
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString

private const val MAX_TEXT_SIZE = 65_535

public inline fun String.toTopic(): Topic = Topic(this)

public inline fun String.toResponseTopic(): ResponseTopic = ResponseTopic(this)

public inline fun String.toResponseInformation(): ResponseInformation = ResponseInformation(this)

public inline fun String.toReasonString(): ReasonString = ReasonString(this)

/**
 * Writes the specified string as an MQTT string, hence writing first the size of the string, then the ZTF-8 encoded
 * string.
 *
 * @throws MalformedPacketException when the byte size of the string is larger than 65,535.
 */
internal fun Sink.writeMqttString(text: String) {
    val size = text.utf8Size()
    if (size > MAX_TEXT_SIZE) {
        throw MalformedPacketException("Text '${text.substring(0..100)}...' is too large: $size (max allowed size: ${MAX_TEXT_SIZE})")
    }

    writeUShort(size.toUShort())
    write(text.encodeToByteString())
}

internal fun Source.readMqttString(): String {
    val bytes = readByteString(readUShort().toInt())

    return bytes.decodeToString()
}

internal fun String.utf8Size(beginIndex: Int = 0, endIndex: Int = length): Int {
    var count = 0
    var i = beginIndex

    while (i < endIndex) {
        val c = this[i].code

        if (c < 0x80) {
            // 7-bit character with 1 byte
            count++
            i++
        } else if (c < 0x800) {
            // 11-bit character with 2 bytes
            count += 2
            i++
        } else if (c < 0xd800 || c > 0xdfff) {
            // 16-bit character with 3 bytes
            count += 3
            i++
        } else {
            val low = if (i + 1 < endIndex) this[i + 1].code else 0
            if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
                // Malformed surrogate, which yields '?'
                count++
                i++
            } else {
                // 21-bit character with 4 bytes
                count += 4
                i += 2
            }
        }
    }

    return count
}