package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PublishResponseTest {

    @Test
    fun `encode and decode returns same packet`() = runTest {
        assertEncodeDecode(Puback(42u, Success))
        assertEncodeDecode(Pubrec(42u, Success))
        assertEncodeDecode(Pubrel(42u, Success))
        assertEncodeDecode(Pubcomp(42u, Success))

        assertEncodeDecode(Puback(42u, NoMatchingSubscribers))
        assertEncodeDecode(Pubrec(42u, NoMatchingSubscribers))
        assertEncodeDecode(Pubrel(42u, NoMatchingSubscribers))
        assertEncodeDecode(Pubcomp(42u, NoMatchingSubscribers))

        assertEncodeDecode(Puback(42u, Success, ReasonString("reason"), buildUserProperties { "key" to "value" }))
        assertEncodeDecode(Pubrec(42u, Success, ReasonString("reason"), buildUserProperties { "key" to "value" }))
        assertEncodeDecode(Pubrel(42u, Success, ReasonString("reason"), buildUserProperties { "key" to "value" }))
        assertEncodeDecode(Pubcomp(42u, Success, ReasonString("reason"), buildUserProperties { "key" to "value" }))

        assertEncodeDecode(Puback(42u, Success, null, buildUserProperties { "key" to "value" }))
        assertEncodeDecode(Pubrec(42u, Success, null, buildUserProperties { "key" to "value" }))
        assertEncodeDecode(Pubrel(42u, Success, null, buildUserProperties { "key" to "value" }))
        assertEncodeDecode(Pubcomp(42u, Success, null, buildUserProperties { "key" to "value" }))
    }

    @Test
    fun `zero packet identifiers are not allowed`() {
        assertFailsWith<MalformedPacketException> { (Puback(0u, Success)) }
        assertFailsWith<MalformedPacketException> { (Pubrec(0u, Success)) }
        assertFailsWith<MalformedPacketException> { (Pubrel(0u, Success)) }
        assertFailsWith<MalformedPacketException> { (Pubcomp(0u, Success)) }
    }
}