package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Represents a name/value pair as specified in the [MQTT specification](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901013).
 */
public data class StringPair(val name: String, val value: String) {

    override fun toString(): String {
        return "$name=$value"
    }
}

/**
 * Infix function to create a [StringPair], hence:
 * ```
 * val stringPair = "name" to "value"
 * ```
 */
public infix fun String.to(that: String): StringPair = StringPair(this, that)

internal fun Sink.write(pair: StringPair) {
    writeMqttString(pair.name)
    writeMqttString(pair.value)
}

internal fun Source.readStringPair() = StringPair(readMqttString(), readMqttString())