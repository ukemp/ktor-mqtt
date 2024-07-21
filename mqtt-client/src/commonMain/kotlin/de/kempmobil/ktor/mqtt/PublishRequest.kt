package de.kempmobil.ktor.mqtt

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString

public data class PublishRequest(
    val topic: Topic,
    val desiredQoS: QoS,
    val payload: ByteString,
    val isRetainMessage: Boolean,
    val messageExpiryInterval: MessageExpiryInterval? = null,
    val topicAlias: TopicAlias? = null,
    val responseTopic: ResponseTopic? = null,
    val correlationData: CorrelationData? = null,
    val subscriptionIdentifier: SubscriptionIdentifier? = null,
    val contentType: ContentType? = null,
    val payloadFormatIndicator: PayloadFormatIndicator? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
)

/**
 * Create a request to send a PUBLISH packet to the server. When `topicAlias` is not null, make sure the specified
 * number is lower than the one sent from the server in CONNACK packet. Otherwise, the publishing will fail.
 *
 * @see MqttClient.serverTopicAliasMaximum
 */
public fun buildPublishRequest(
    topicName: String,
    topicAlias: UShort? = null,
    init: PublishRequestBuilder.() -> Unit
): PublishRequest {
    return PublishRequestBuilder(topicName, topicAlias).also(init).build()
}

public class PublishRequestBuilder(
    private val topicName: String = "",
    private var topicAlias: UShort? = null
) {
    public var desiredQoS: QoS = QoS.AT_MOST_ONCE

    public var isRetainMessage: Boolean = false

    public var messageExpiryInterval: Int? = null

    public var responseTopic: String? = null

    public var correlationData: ByteString? = null

    public var subscriptionIdentifier: Int? = null

    public var contentType: String? = null

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

    public fun build(): PublishRequest {
        return PublishRequest(
            topic = Topic(topicName),
            desiredQoS = desiredQoS,
            payload = payload,
            isRetainMessage = isRetainMessage,
            messageExpiryInterval = messageExpiryInterval?.let { MessageExpiryInterval(it) },
            topicAlias = topicAlias?.let { TopicAlias(it) },
            responseTopic = responseTopic?.let { ResponseTopic(it) },
            correlationData = correlationData?.let { CorrelationData(it) },
            subscriptionIdentifier = subscriptionIdentifier?.let { SubscriptionIdentifier(it) },
            contentType = contentType?.let { ContentType(it) },
            payloadFormatIndicator = payloadFormatIndicator,
            userProperties = userProperties
        )
    }

    private companion object {

        val EMPTY_PAYLOAD = ByteString(ByteArray(0))
    }
}