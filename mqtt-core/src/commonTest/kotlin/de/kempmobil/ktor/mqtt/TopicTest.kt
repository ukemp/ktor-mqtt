package de.kempmobil.ktor.mqtt

import kotlin.test.*

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
    fun `shared filter detected`() {
        assertFalse(Topic("abc/def").isShared())               // Not a share
        assertFalse(Topic($$"$share").isShared())              // Missing share name
        assertFalse(Topic($$"$share/").isShared())             // Missing share name
        assertFalse(Topic($$"$share/name#").isShared())        // Invalid share name
        assertFalse(Topic($$"$share/name+/filter").isShared()) // Invalid share name
        assertFalse(Topic($$"$share/name/").isShared())        // Missing filter

        assertTrue(Topic($$"$share/name/filter").isShared())
        assertTrue(Topic($$"$share/name/filter#").isShared())
        assertTrue(Topic($$"$share/name/filter/+").isShared())
    }

    @Test
    fun `return share name and filter`() {
        val (name, filter) = Topic($$"$share/consumer1/sport/tennis/+").shareNameAndFilter()
        assertEquals("consumer1", name)
        assertEquals(Topic("sport/tennis/+"), filter)

        assertFailsWith<IllegalStateException> { Topic("abc/def").shareNameAndFilter() }
    }

    @Test
    fun `toString returns topic name only`() {
        val name = "test/topic"
        assertEquals(name, Topic(name).toString())
    }
}