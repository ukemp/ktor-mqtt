package de.kempmobil.ktor.mqtt.util

import de.kempmobil.ktor.mqtt.MalformedPacketException
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.*

class StringTest {

    @Test
    fun `encode a MQTT string`() {
        val text = "¡Hola!" // The inverted exclamation mark is represented as 0xC2 (194), 0xA1 (161) in unicode
        val builder = BytePacketBuilder()

        builder.writeMqttString(text)
        assertEquals(9, builder.size) // 2 bytes for the size and 7 bytes for teh string itself

        val reader = builder.build()
        assertEquals(0, reader.readByte())
        assertEquals(7, reader.readByte())
        assertEquals(194.toByte(), reader.readByte())
        assertEquals(161.toByte(), reader.readByte())
        assertEquals(72, reader.readByte())
        assertEquals(111, reader.readByte())
        assertEquals(108, reader.readByte())
        assertEquals(97, reader.readByte())
        assertEquals(33, reader.readByte())
    }

    @Test
    fun `decode a MQTT string`() {
        val bytes = buildPacket {
            writeFully(byteArrayOf(0, 7, 194.toByte(), 161.toByte(), 72, 111, 108, 97, 33))
        }
        assertEquals("¡Hola!", bytes.readMqttString())
    }

    @Test
    fun `fail when string is too large`() {
        val tooLarge = (CharArray(65_536) { 'x' }).concatToString()
        val builder = BytePacketBuilder()

        assertFailsWith<MalformedPacketException> { builder.writeMqttString(tooLarge) }
    }
}