package de.kempmobil.ktor.mqtt

import io.ktor.utils.io.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

class StringPairTest {

    @Test
    fun `encode MQTT string pair`() {
        val pair = "key" to "value"
        val reader = buildPacket {
            write(pair)
        }
        assertEquals(12, reader.remaining)
        assertEquals(0, reader.readByte())
        assertEquals(3, reader.readByte())
        assertEquals(107, reader.readByte())
        assertEquals(101, reader.readByte())
        assertEquals(121, reader.readByte())
        assertEquals(0, reader.readByte())
        assertEquals(5, reader.readByte())
        assertEquals(118, reader.readByte())
        assertEquals(97, reader.readByte())
        assertEquals(108, reader.readByte())
        assertEquals(117, reader.readByte())
        assertEquals(101, reader.readByte())
    }

    @Test
    fun `decode a MQTT string pair`() {
        val reader = buildPacket {
            writeFully(byteArrayOf(0, 3, 107, 101, 121, 0, 5, 118, 97, 108, 117, 101))
        }
        val actual = reader.readStringPair()
        assertEquals("key" to "value", actual)
    }
}