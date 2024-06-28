package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.readMqttByteString
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttByteString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString

public data class WillMessage(
    public val topic: Topic,
    public val payload: ByteString,
    public val properties: WillProperties
)

public fun buildWillMessage(topic: String, init: WillMessageBuilder.() -> Unit): WillMessage {
    val builder = WillMessageBuilder(topic)
    builder.init()
    return builder.build()
}

public class WillMessageBuilder(private val topic: String) {

    private val propertiesBuilder = WillPropertiesBuilder()

    public var payload: ByteString = EMPTY_PAYLOAD

    public fun properties(init: WillPropertiesBuilder.() -> Unit) {
        propertiesBuilder.init()
    }

    public fun build(): WillMessage {
        return WillMessage(Topic(topic), payload, propertiesBuilder.build())
    }

    private companion object {

        val EMPTY_PAYLOAD = ByteString(ByteArray(0))
    }
}

internal fun BytePacketBuilder.write(willMessage: WillMessage) {
    writeProperties(*willMessage.properties.asArray())
    writeMqttString(willMessage.topic.name)
    writeMqttByteString(willMessage.payload)
}

internal fun ByteReadPacket.readWillMessage(): WillMessage {
    val properties = readProperties()
    val topic = readMqttString()
    val payload = readMqttByteString()

    return WillMessage(Topic(topic), payload, WillProperties.from(properties))
}