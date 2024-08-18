package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PublishTest {

    @Test
    fun `encode and decode returns same packet`() = runTest {
        assertEncodeDecode(Publish(topic = Topic("test/topic"), payload = "123".encodeToByteString()))
        assertEncodeDecode(
            Publish(
                isDupMessage = true,
                qoS = QoS.EXACTLY_ONE,
                isRetainMessage = true,
                packetIdentifier = 74u,
                topic = Topic("test/topic"),
                payloadFormatIndicator = PayloadFormatIndicator.UTF_8,
                messageExpiryInterval = MessageExpiryInterval(60u),
                topicAlias = TopicAlias(3u),
                responseTopic = ResponseTopic("response"),
                correlationData = CorrelationData("123".encodeToByteString()),
                userProperties = buildUserProperties { "user" to "value" },
                subscriptionIdentifier = SubscriptionIdentifier(5000),
                payload = ByteString("payload".toByteArray())
            )
        )
    }

    @Test
    fun `packet identifiers are not null when required by QoS`() {
        val topic = Topic("abc/def")
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
}