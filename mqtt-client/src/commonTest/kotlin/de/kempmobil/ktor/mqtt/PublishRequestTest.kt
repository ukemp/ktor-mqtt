package de.kempmobil.ktor.mqtt

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PublishRequestTest {

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