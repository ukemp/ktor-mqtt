package de.kempmobil.ktor.mqtt.util

import io.ktor.utils.io.core.*
import io.ktor.utils.io.readFully
import kotlinx.coroutines.test.runTest
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
            val reader = buildPacket {
                writeVariableByteInt(data.first)
            }
            val actual = ByteArray(data.second.size)
            reader.readFully(actual)

            assertEquals(data.second.size, actual.size)
            data.second.forEachIndexed { index, byte ->
                assertEquals(byte, actual[index])
            }
        }
    }

    @Test
    fun `decode a MQTT Int`() {
        sample.forEach { data ->
            val reader = buildPacket {
                writeFully(data.second)
            }
            val actual = reader.readVariableByteInt()
            assertEquals(data.first, actual)
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