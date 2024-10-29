package de.kempmobil.ktor.mqtt.util

import kotlinx.io.Buffer
import kotlinx.io.readByteString
import kotlin.test.Test
import kotlin.test.assertEquals

class IntTest {

    private val sample = listOf(
        1 to byteArrayOf(0x01),
        127 to byteArrayOf(0x7F),
        128 to byteArrayOf(0x80.toByte(), 0x01),
        16_383 to byteArrayOf(0xFF.toByte(), 0x7F),
        16_384 to byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x01),
        2_097_151 to byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x7F),
        2_097_152 to byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01),
        268_435_455 to byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)
    )

    @Test
    fun `encode a MQTT Int`() {
        sample.forEach { data ->
            with(Buffer()) {
                writeVariableByteInt(data.first)
                val actual = readByteString(data.second.size)

                assertEquals(data.second.size, actual.size)
                data.second.forEachIndexed { index, byte ->
                    assertEquals(byte, actual[index])
                }
            }
        }
    }

    @Test
    fun `decode a MQTT Int`() {
        sample.forEach { data ->
            with(Buffer()) {
                write(data.second)
                val actual = readVariableByteInt()
                assertEquals(data.first, actual)
            }
        }
    }

    @Test
    fun `variable byte size`() {
        sample.forEach { data ->
            val actual = data.first.variableByteIntSize()
            assertEquals(data.second.size, actual)
        }
    }
}