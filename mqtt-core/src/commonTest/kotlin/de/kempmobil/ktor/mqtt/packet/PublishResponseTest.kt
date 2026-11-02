package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublishResponseTest {

    @Test
    fun `response factories preserve publish user properties`() = runTest {
        val properties = buildUserProperties {
            "message-type" to "status"
            "schema-version" to "1"
            "correlation-id" to "test-correlation"
        }
        val publish = Publish(
            qoS = QoS.AT_LEAST_ONCE,
            packetIdentifier = 42u,
            topic = Topic("test/status"),
            userProperties = properties,
            payload = ByteString()
        )

        listOf(
            Puback.from(publish, NoMatchingSubscribers, "reason"),
            Pubrec.from(publish, NoMatchingSubscribers, "reason"),
            Pubrel.from(publish, NoMatchingSubscribers, "reason"),
            Pubcomp.from(publish, NoMatchingSubscribers, "reason"),
            Pubcomp.from(Pubrel(42u, Success, userProperties = properties), NoMatchingSubscribers, "reason")
        ).forEach { response ->
            assertEquals(42.toUShort(), response.packetIdentifier)
            assertEquals(NoMatchingSubscribers, response.reason)
            assertEquals(ReasonString("reason"), response.reasonString)
            assertEquals(properties, response.userProperties)
            assertEncodeDecodeOf(response)
        }
        assertEquals(properties, publish.userProperties)
    }

    @Test
    fun `encode and decode returns same packet`() = runTest {
        assertEncodeDecodeOf(Puback(42u, Success))
        assertEncodeDecodeOf(Pubrec(42u, Success))
        assertEncodeDecodeOf(Pubrel(42u, Success))
        assertEncodeDecodeOf(Pubcomp(42u, Success))

        assertEncodeDecodeOf(Puback(42u, NoMatchingSubscribers))
        assertEncodeDecodeOf(Pubrec(42u, NoMatchingSubscribers))
        assertEncodeDecodeOf(Pubrel(42u, NoMatchingSubscribers))
        assertEncodeDecodeOf(Pubcomp(42u, NoMatchingSubscribers))

        assertEncodeDecodeOf(Puback(42u, Success, ReasonString("reason"), buildUserProperties { "key" to "value" }))
        assertEncodeDecodeOf(Pubrec(42u, Success, ReasonString("reason"), buildUserProperties { "key" to "value" }))
        assertEncodeDecodeOf(Pubrel(42u, Success, ReasonString("reason"), buildUserProperties { "key" to "value" }))
        assertEncodeDecodeOf(Pubcomp(42u, Success, ReasonString("reason"), buildUserProperties { "key" to "value" }))

        assertEncodeDecodeOf(Puback(42u, Success, null, buildUserProperties { "key" to "value" }))
        assertEncodeDecodeOf(Pubrec(42u, Success, null, buildUserProperties { "key" to "value" }))
        assertEncodeDecodeOf(Pubrel(42u, Success, null, buildUserProperties { "key" to "value" }))
        assertEncodeDecodeOf(Pubcomp(42u, Success, null, buildUserProperties { "key" to "value" }))
    }

    @Test
    fun `zero packet identifiers are not allowed`() {
        assertFailsWith<MalformedPacketException> { (Puback(0u, Success)) }
        assertFailsWith<MalformedPacketException> { (Pubrec(0u, Success)) }
        assertFailsWith<MalformedPacketException> { (Pubrel(0u, Success)) }
        assertFailsWith<MalformedPacketException> { (Pubcomp(0u, Success)) }
    }
}