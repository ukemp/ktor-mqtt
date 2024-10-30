package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.toResponseTopic
import de.kempmobil.ktor.mqtt.util.toTopic
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PublishTest {

    @Test
    fun `encode and decode returns same packet`() {
        assertEncodeDecodeOf(Publish(topic = "test/topic".toTopic(), payload = "123".encodeToByteString()))
        assertEncodeDecodeOf(
            Publish(
                isDupMessage = true,
                qoS = QoS.EXACTLY_ONE,
                isRetainMessage = true,
                packetIdentifier = 74u,
                topic = "test/topic".toTopic(),
                payloadFormatIndicator = PayloadFormatIndicator.UTF_8,
                messageExpiryInterval = MessageExpiryInterval(60u),
                topicAlias = TopicAlias(3u),
                responseTopic = "response".toResponseTopic(),
                correlationData = CorrelationData("123".encodeToByteString()),
                userProperties = buildUserProperties { "user" to "value" },
                subscriptionIdentifier = SubscriptionIdentifier(5000),
                payload = ByteString("payload".toByteArray())
            )
        )
    }

    @Test
    fun `packet identifiers are not null when required by QoS`() {
        val topic = "abc/def".toTopic()
        val payload = "123".encodeToByteString()

        // Must NOT throw an exception
        Publish(qoS = QoS.AT_MOST_ONCE, packetIdentifier = null, topic = topic, payload = payload)
        Publish(qoS = QoS.AT_LEAST_ONCE, packetIdentifier = 1u, topic = topic, payload = payload)
        Publish(qoS = QoS.EXACTLY_ONE, packetIdentifier = 1u, topic = topic, payload = payload)

        assertFailsWith<MalformedPacketException> {
            Publish(qoS = QoS.AT_MOST_ONCE, packetIdentifier = 1u, topic = topic, payload = payload)
        }
        assertFailsWith<MalformedPacketException> {
            Publish(qoS = QoS.AT_LEAST_ONCE, packetIdentifier = null, topic = topic, payload = payload)
        }
        assertFailsWith<MalformedPacketException> {
            Publish(qoS = QoS.EXACTLY_ONE, packetIdentifier = null, topic = topic, payload = payload)
        }
    }

    @Test
    fun `either topic or topic alias must be set`() {
        assertFailsWith<MalformedPacketException> {
            Publish(
                qoS = QoS.AT_MOST_ONCE,
                topic = "".toTopic(),
                topicAlias = null,
                payload = "123".encodeToByteString()
            )
        }
    }

    @Test
    fun `a topic alias of zero is not allowed`() {
        val payload = "123".encodeToByteString()
        assertFailsWith<MalformedPacketException> {
            Publish(qoS = QoS.AT_MOST_ONCE, topic = "abc".toTopic(), topicAlias = TopicAlias(0u), payload = payload)
        }
    }
}