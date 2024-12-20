package de.kempmobil.ktor.mqtt.util

import de.kempmobil.ktor.mqtt.MalformedPacketException
import io.ktor.utils.io.core.*
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteStringTest {

    @Test
    fun `write MQTT byte string`() {
        val data = ByteString(1, 2, 3, 4, 5, 6, 7)
        val reader = buildPacket {
            writeMqttByteString(data)
        }
        assertEquals(0, reader.readByte())
        assertEquals(7, reader.readByte())
        assertEquals(1, reader.readByte())
        assertEquals(2, reader.readByte())
        assertEquals(3, reader.readByte())
        assertEquals(4, reader.readByte())
        assertEquals(5, reader.readByte())
        assertEquals(6, reader.readByte())
        assertEquals(7, reader.readByte())
    }

    @Test
    fun `read MQTT byte string`() {
        val reader = buildPacket {
            writeFully(byteArrayOf(0, 7, 1, 2, 3, 4, 5, 6, 7))
        }
        val actual = reader.readMqttByteString()
        assertEquals(7, actual.size)
        assertEquals(1, actual[0])
        assertEquals(2, actual[1])
        assertEquals(3, actual[2])
        assertEquals(4, actual[3])
        assertEquals(5, actual[4])
        assertEquals(6, actual[5])
        assertEquals(7, actual[6])
    }

    @Test
    fun `fail with MalformedPacketException when byte string is too large`() {
        val tooLarge = ByteString(ByteArray(65_536))

        assertFailsWith<MalformedPacketException> { Buffer().writeMqttByteString(tooLarge) }
    }

    @Test
    fun `encode and decode a large byte string`() {
        val bytes = ByteArray(65_535)
        bytes[42] = 17
        val expected = ByteString(bytes)

        with(Buffer()) {
            writeMqttByteString(expected)
            val actual = readMqttByteString()
            assertEquals(expected, actual)
            assertEquals(17, actual[42])
        }
    }
}