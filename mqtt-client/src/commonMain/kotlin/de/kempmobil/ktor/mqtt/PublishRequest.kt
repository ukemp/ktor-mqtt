package de.kempmobil.ktor.mqtt

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString

public data class PublishRequest(
    val desiredQoS: QoS = QoS.AT_MOST_ONCE,
    val topicName: String,
) {
    public var isRetainMessage: Boolean = false

    public var messageExpiryInterval: MessageExpiryInterval? = null

    public var topicAlias: TopicAlias? = null

    public var responseTopic: ResponseTopic? = null

    public var correlationData: CorrelationData? = null

    public var subscriptionIdentifier: SubscriptionIdentifier? = null

    public var contentType: ContentType? = null

    internal var payload: ByteString = EMPTY_PAYLOAD

    internal var payloadFormatIndicator: PayloadFormatIndicator = PayloadFormatIndicator.NONE

    internal var userProperties: UserProperties = UserProperties.EMPTY

    /**
     * Convenience method to define text as payload, also sets the [PayloadFormatIndicator] to `UTF_8`.
     */
    public fun payload(text: String) {
        this.payload = text.encodeToByteString()
        this.payloadFormatIndicator = PayloadFormatIndicator.UTF_8
    }

    /**
     * Defines the payload and the payload format indicator of the publish request. If `byteString` represents a UTF-8
     * encoded text, you should set the payload format indicator to [PayloadFormatIndicator.UTF_8] or use [payload],
     * which automatically converts text and sets the format indicator.
     */
    public fun payload(
        byteString: ByteString,
        payloadFormatIndicator: PayloadFormatIndicator = PayloadFormatIndicator.NONE
    ) {
        this.payload = byteString
        this.payloadFormatIndicator = payloadFormatIndicator
    }

    public fun userProperties(init: UserPropertiesBuilder.() -> Unit) {
        userProperties = UserPropertiesBuilder().also(init).build()
    }

    private companion object {

        val EMPTY_PAYLOAD = ByteString(ByteArray(0))
    }
}