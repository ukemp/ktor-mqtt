package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PublishResponseTest {

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