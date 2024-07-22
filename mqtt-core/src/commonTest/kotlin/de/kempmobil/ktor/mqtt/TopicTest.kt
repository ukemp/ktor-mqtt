package de.kempmobil.ktor.mqtt

import kotlin.test.Test
import kotlin.test.assertEquals

class TopicTest {

    @Test
    fun `wildcards detected`() {
        listOf(
            "" to false,
            "sport/tennis" to false,
            "#" to true,
            "+" to true,
            "#+" to true,
            "sport/tennis/player1/#" to true,
            "sport/tennis/+" to true
        ).forEach {
            assertEquals(it.second, Topic(it.first).containsWildcard(), "'${it.first}' wildcard not matching")
        }
    }

    @Test
    fun `toString returns topic name only`() {
        val name = "test/topic"
        assertEquals(name, Topic(name).toString())
    }
}