package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.readMqttByteString
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttByteString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString

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

    private var payload: ByteString = EMPTY_PAYLOAD

    /**
     * Configures the will properties.
     */
    public fun properties(init: WillPropertiesBuilder.() -> Unit) {
        propertiesBuilder.init()
    }

    /**
     * Convenience method to define a text as payload, also sets the [PayloadFormatIndicator] of the will properties
     * to `UTF_8`.
     */
    public fun payload(text: String) {
        this.payload = text.encodeToByteString()
        this.propertiesBuilder.payloadFormatIndicator = PayloadFormatIndicator.UTF_8
    }

    /**
     * Defines the payload (without setting the payload format indicator of the will properties).
     */
    public fun payload(byteString: ByteString) {
        this.payload = byteString
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