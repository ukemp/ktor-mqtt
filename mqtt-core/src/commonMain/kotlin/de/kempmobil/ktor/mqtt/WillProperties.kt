package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.MqttDslMarker
import kotlinx.io.bytestring.ByteString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

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

/**
 * DSL for building will properties.
 *
 * @property willDelayInterval the Server delays publishing the Client’s Will Message until the Will Delay Interval has
 *           passed or the Session ends, whichever happens first; defaults to 0
 * @property payloadFormatIndicator specifies the format of the will message payload
 * @property messageExpiryInterval the lifetime of the will message and is sent as the publication expiry interval when
 *           the Server publishes the Will Message
 * @property contentType string describing the content of the Will Message
 * @property responseTopic topic name for a response message
 * @property correlationData used by the sender of the Request Message to identify which request the Response Message is
 *           for when it is received
 */
@MqttDslMarker
@Suppress("MemberVisibilityCanBePrivate")
public class WillPropertiesBuilder(
    public var willDelayInterval: Duration = ZERO,
    public var payloadFormatIndicator: PayloadFormatIndicator? = null,
    public var messageExpiryInterval: Duration? = null,
    public var contentType: String? = null,
    public var responseTopic: String? = null,
    public var correlationData: ByteString? = null
) {
    private val userPropertiesBuilder = UserPropertiesBuilder()

    /**
     * Creates the user properties of this will properties.
     */
    public fun userProperties(init: UserPropertiesBuilder.() -> Unit) {
        userPropertiesBuilder.init()
    }

    public fun build(): WillProperties {
        return WillProperties(
            willDelayInterval = WillDelayInterval(willDelayInterval.inWholeSeconds.toUInt()),
            payloadFormatIndicator = payloadFormatIndicator,
            messageExpiryInterval = messageExpiryInterval?.let { MessageExpiryInterval(it.inWholeSeconds.toUInt()) },
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
