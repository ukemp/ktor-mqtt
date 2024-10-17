package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.MqttDslMarker
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlin.time.Duration

public data class PublishRequest(
    val topic: Topic,
    val desiredQoS: QoS,
    val payload: ByteString,
    val isRetainMessage: Boolean,
    val messageExpiryInterval: MessageExpiryInterval? = null,
    val topicAlias: TopicAlias? = null,
    val responseTopic: ResponseTopic? = null,
    val correlationData: CorrelationData? = null,
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

@MqttDslMarker
public class PublishRequestBuilder(
    private val topicName: String = "",
    private var topicAlias: UShort? = null
) {
    public var desiredQoS: QoS = QoS.AT_MOST_ONCE

    public var isRetainMessage: Boolean = false

    public var messageExpiryInterval: Duration? = null

    public var responseTopic: String? = null

    public var correlationData: ByteString? = null

    public var contentType: String? = null

    internal var payload: ByteString = EMPTY_PAYLOAD

    internal var payloadFormatIndicator: PayloadFormatIndicator? = null

    internal var userProperties: UserProperties = UserProperties.EMPTY

    /**
     * Convenience method to define text as payload, also sets the [PayloadFormatIndicator] to `UTF_8`.
     */
    public fun payload(text: String) {
        this.payload = text.encodeToByteString()
        this.payloadFormatIndicator = PayloadFormatIndicator.UTF_8
    }

    /**
     * Defines the payload (without setting the payload format indicator of the publish request).
     */
    public fun payload(byteString: ByteString) {
        this.payload = byteString
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
            messageExpiryInterval = messageExpiryInterval?.let { MessageExpiryInterval(it.inWholeSeconds.toUInt()) },
            topicAlias = topicAlias?.let { TopicAlias(it) },
            responseTopic = responseTopic?.let { ResponseTopic(it) },
            correlationData = correlationData?.let { CorrelationData(it) },
            contentType = contentType?.let { ContentType(it) },
            payloadFormatIndicator = payloadFormatIndicator,
            userProperties = userProperties
        )
    }

    private companion object {

        val EMPTY_PAYLOAD = ByteString(ByteArray(0))
    }
}