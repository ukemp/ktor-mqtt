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
public fun PublishRequest(
    topicName: String,
    topicAlias: UShort? = null,
    init: PublishRequestBuilder.() -> Unit
): PublishRequest {
    val topic = Topic(topicName)
    if (topic.containsWildcard()) {
        throw IllegalArgumentException("Topic Name in PUBLISH packet contains wildcard characters [MQTT-3.3.2-2]: '$topicName'")
    }
    return PublishRequestBuilder(topic, topicAlias).also(init).build()
}

@MqttDslMarker
@Suppress("MemberVisibilityCanBePrivate")
public class PublishRequestBuilder(
    private val topic: Topic,
    private var topicAlias: UShort? = null
) {
    /**
     * The desired quality of service level for this publish message. The actual QoS is bound by [MqttClient.maxQos]
     */
    public var desiredQoS: QoS = QoS.AT_MOST_ONCE

    /**
     * Defines the retain flag in the publish header.
     */
    public var isRetainMessage: Boolean = false

    /**
     * Defines the lifetime of the Application Message.
     */
    public var messageExpiryInterval: Duration? = null

    /**
     * Used as the Topic Name for a response message.
     */
    public var responseTopic: String? = null

    /**
     * The Correlation Data is used by the sender of the Request Message to identify which request the Response Message
     * is for when it is received.
     */
    public var correlationData: ByteString? = null

    /**
     * A string describing the content of the Application Message.
     */
    public var contentType: String? = null

    /**
     * Defines the [PayloadFormatIndicator], when `null` a value of `0x00` will be used.
     *
     * @see payload()
     */
    public var payloadFormatIndicator: PayloadFormatIndicator? = null

    internal var payload: ByteString = EMPTY_PAYLOAD

    internal var userProperties: UserProperties = UserProperties.EMPTY

    /**
     * Define text as payload and sets the [PayloadFormatIndicator] to `UTF_8`.
     *
     * When the payload is not defined, an empty payload will be used.
     */
    public fun payload(text: String) {
        this.payload = text.encodeToByteString()
        this.payloadFormatIndicator = PayloadFormatIndicator.UTF_8
    }

    /**
     * Defines the payload and uses a [PayloadFormatIndicator] of `0x00` (i.e., unspecified bytes).
     *
     * When the payload is not defined, an empty payload will be used.
     */
    public fun payload(byteString: ByteString) {
        this.payload = byteString
    }

    public fun userProperties(init: UserPropertiesBuilder.() -> Unit) {
        userProperties = UserPropertiesBuilder().also(init).build()
    }

    public fun build(): PublishRequest {
        return PublishRequest(
            topic = topic,
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