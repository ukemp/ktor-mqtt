package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.writeVariableByteInt
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString

public data class WillProperties(
    public val willDelayInterval: WillDelayInterval,
    public val payloadFormatIndicator: PayloadFormatIndicator?,
    public val messageExpiryInterval: MessageExpiryInterval?,
    public val contentType: ContentType?,
    public val responseTopic: ResponseTopic?,
    public val correlationData: CorrelationData?,
    public val userProperties: UserProperties
)

public fun buildWillProperties(init: WillPropertiesBuilder.() -> Unit): WillProperties {
    val builder = WillPropertiesBuilder()
    builder.init()
    return builder.build()
}

public class WillPropertiesBuilder(
    public var willDelayInterval: Int = 0,
    public var payloadFormatIndicator: Byte? = null,
    public var messageExpiryInterval: Int? = null,
    public var contentType: String? = null,
    public var responseTopic: String? = null,
    public var correlationData: ByteString? = null
) {
    private val userPropertiesBuilder = UserPropertiesBuilder()

    public fun userProperties(init: UserPropertiesBuilder.() -> Unit) {
        userPropertiesBuilder.init()
    }

    public fun build(): WillProperties {
        return WillProperties(
            willDelayInterval = WillDelayInterval(willDelayInterval),
            payloadFormatIndicator = if (payloadFormatIndicator != null) PayloadFormatIndicator(payloadFormatIndicator!!) else null,
            messageExpiryInterval = if (messageExpiryInterval != null) MessageExpiryInterval(messageExpiryInterval!!) else null,
            contentType = if (contentType != null) ContentType(contentType!!) else null,
            responseTopic = if (responseTopic != null) ResponseTopic(responseTopic!!) else null,
            correlationData = if (correlationData != null) CorrelationData(correlationData!!) else null,
            userProperties = userPropertiesBuilder.build()
        )
    }
}

internal fun WillProperties.byteCount() =
    willDelayInterval.byteCount + payloadFormatIndicator.byteCount + messageExpiryInterval.byteCount +
            contentType.byteCount + responseTopic.byteCount + correlationData.byteCount + userProperties.byteCount()

internal fun BytePacketBuilder.write(willProperties: WillProperties) {
    with(willProperties) {
        writeVariableByteInt(byteCount())
        write(willDelayInterval)
        if (payloadFormatIndicator != null) write(payloadFormatIndicator)
        if (messageExpiryInterval != null) write(messageExpiryInterval)
        if (contentType != null) write(contentType)
        if (responseTopic != null) write(responseTopic)
        if (correlationData != null) write(correlationData)
        write(userProperties)
    }
}