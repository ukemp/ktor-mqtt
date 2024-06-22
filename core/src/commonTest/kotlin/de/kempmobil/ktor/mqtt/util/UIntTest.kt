package de.kempmobil.ktor.mqtt.util

import io.ktor.utils.io.core.*
import io.ktor.utils.io.readFully
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UIntTest {

    private val sample = listOf(
        1u to byteArrayOf(0x01),
        127u to byteArrayOf(0x7F),
        128u to byteArrayOf(0x80.toByte(), 0x01),
        16_383u to byteArrayOf(0xFF.toByte(), 0x7F),
        16_384u to byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x01),
        2_097_151u to byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x7F),
        2_097_152u to byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01),
        268_435_455u to byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)
    )

    @Test
    fun `encode a MQTT UInt`() {
        sample.forEach { data ->
            val reader = buildPacket {
                writeVariableByteUInt(data.first)
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
    fun `decode a MQTT UInt`() {
        sample.forEach { data ->
            val reader = buildPacket {
                writeFully(data.second)
            }
            val actual = reader.readVariableByteUInt()
            assertEquals(data.first, actual)
        }
    }
}