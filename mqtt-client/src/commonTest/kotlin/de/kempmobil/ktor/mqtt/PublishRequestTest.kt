package de.kempmobil.ktor.mqtt

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PublishRequestTest {

    @Test
    fun `a request without a message expiry interval creates a message that does not expire`() {
        // [MQTT-3.3.2.3.3] "If absent, the Application Message does not expire"
        val request = PublishRequest("test/topic") { }

        assertNull(request.messageExpiryInterval)
    }

    @Test
    fun `cannot create publish request with invalid topic name`() {
        listOf(
            "#",
            "+",
            "sport/#",
            "sport/+/player1"
        ).forEach {
            assertFailsWith<IllegalArgumentException> {
                PublishRequest(it) { }
            }
        }
    }
}