package de.kempmobil.ktor.mqtt

import kotlinx.io.bytestring.ByteString

public data class WillProperties(
    public val willDelayInterval: WillDelayInterval,
    public val payloadFormatIndicator: PayloadFormatIndicator?,
    public val messageExpiryInterval: MessageExpiryInterval?,
    public val contentType: ContentType?,
    public val responseTopic: ResponseTopic?,
    public val correlationData: CorrelationData?,
    public val userProperties: UserProperties
) {
    internal companion object {

        internal fun from(properties: List<Property<*>>): WillProperties {
            return WillProperties(
                willDelayInterval = properties.single<WillDelayInterval>(),
                payloadFormatIndicator = properties.singleOrNull<PayloadFormatIndicator>(),
                messageExpiryInterval = properties.singleOrNull<MessageExpiryInterval>(),
                contentType = properties.singleOrNull<ContentType>(),
                responseTopic = properties.singleOrNull<ResponseTopic>(),
                correlationData = properties.singleOrNull<CorrelationData>(),
                userProperties = UserProperties.from(properties)
            )
        }
    }
}

public fun buildWillProperties(init: WillPropertiesBuilder.() -> Unit): WillProperties {
    val builder = WillPropertiesBuilder()
    builder.init()
    return builder.build()
}

public class WillPropertiesBuilder(
    public var willDelayInterval: UInt = 0u,
    public var payloadFormatIndicator: PayloadFormatIndicator? = null,
    public var messageExpiryInterval: UInt? = null,
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
            payloadFormatIndicator = payloadFormatIndicator,
            messageExpiryInterval = messageExpiryInterval?.let { MessageExpiryInterval(it) },
            contentType = contentType?.let { ContentType(it) },
            responseTopic = responseTopic?.let { ResponseTopic(it) },
            correlationData = correlationData?.let { CorrelationData(it) },
            userProperties = userPropertiesBuilder.build()
        )
    }
}

internal fun WillProperties.asArray(): Array<Property<*>?> {
    return arrayOf(
        willDelayInterval,
        payloadFormatIndicator,
        messageExpiryInterval,
        contentType,
        responseTopic,
        correlationData,
        *userProperties.asArray
    )
}
