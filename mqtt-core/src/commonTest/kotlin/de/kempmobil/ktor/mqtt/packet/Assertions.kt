package de.kempmobil.ktor.mqtt.packet

import io.ktor.utils.io.*
import kotlin.test.assertEquals

/**
 * Writes the specified packet and re-reads it, asserts that the decoded packet is equal to the original packet.
 */
suspend fun assertEncodeDecode(packet: Packet) {
    val writer = ByteChannel(autoFlush = true)
    val reader = ByteChannel(autoFlush = true)
    writer.write(packet)
    writer.close()
    writer.copyTo(reader)
    val actual = reader.readPacket()

    assertEquals(packet, actual)
}