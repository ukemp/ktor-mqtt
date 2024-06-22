package de.kempmobil.ktor.mqtt

import kotlinx.io.bytestring.ByteString

public data class WillMessage(
    public val topic: String,
    public val payload: ByteString,
    public val properties: WillProperties
)

public fun buildWillMessage(topic: String, payload: ByteString, init: WillMessageBuilder.() -> Unit): WillMessage {
    val builder = WillMessageBuilder(topic, payload)
    builder.init()
    return builder.build()
}

public class WillMessageBuilder(private val topic: String, private val payload: ByteString) {

    private val propertiesBuilder = WillPropertiesBuilder()

    public fun properties(init: WillPropertiesBuilder.() -> Unit) {
        propertiesBuilder.init()
    }

    public fun build(): WillMessage {
        return WillMessage(topic, payload, propertiesBuilder.build())
    }
}