package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.MalformedPacketException
import de.kempmobil.ktor.mqtt.QoS
import de.kempmobil.ktor.mqtt.Topic
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PublishTest {

    @Test
    fun `packet identifiers are not null when required by QoS`() {
        val topic = Topic("abc/def")
        val payload = "123".encodeToByteString()

        // Must NOT throw an exception
        Publish(qoS = QoS.AT_MOST_ONCE, packetIdentifier = null, topic = topic, payload = payload)
        Publish(qoS = QoS.AT_MOST_ONCE, packetIdentifier = 1u, topic = topic, payload = payload)

        Publish(qoS = QoS.AT_LEAST_ONCE, packetIdentifier = 1u, topic = topic, payload = payload)
        Publish(qoS = QoS.EXACTLY_ONE, packetIdentifier = 1u, topic = topic, payload = payload)

        // Must throw an exception
        assertFailsWith<MalformedPacketException> {
            Publish(qoS = QoS.AT_LEAST_ONCE, packetIdentifier = null, topic = topic, payload = payload)
        }
        assertFailsWith<MalformedPacketException> {
            Publish(qoS = QoS.EXACTLY_ONE, packetIdentifier = null, topic = topic, payload = payload)
        }
    }
}