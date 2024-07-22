package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*

public data class StringPair(val name: String, val value: String) {

    override fun toString(): String {
        return "$name=$value"
    }
}

public infix fun String.to(that: String): StringPair = StringPair(this, that)

internal fun BytePacketBuilder.write(pair: StringPair) {
    writeMqttString(pair.name)
    writeMqttString(pair.value)
}

internal fun ByteReadPacket.readStringPair() = StringPair(readMqttString(), readMqttString())