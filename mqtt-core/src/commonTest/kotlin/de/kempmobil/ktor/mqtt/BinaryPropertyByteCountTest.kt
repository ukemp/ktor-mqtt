package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Auth
import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.readPacket
import de.kempmobil.ktor.mqtt.packet.write
import de.kempmobil.ktor.mqtt.util.toTopic
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for the encoded size of the two *binary* MQTT 5 properties: Correlation Data
 * (`0x09`) and Authentication Data (`0x16`).
 *
 * Both are written as one identifier byte, a two byte length prefix and then the data itself, so they
 * occupy `size + 3` bytes -- but both reported `size + 1`, leaving the length prefix unaccounted. They
 * are the only two binary properties, which is why the string properties (Content Type, Response
 * Topic) were unaffected.
 *
 * `byteCount()` feeds the encoder, which writes it as the property block's variable byte integer, and
 * the decoder, which counts the same block down by it. The same wrong constant was therefore applied
 * to both sides and the error cancelled: [de.kempmobil.ktor.mqtt.packet.PublishTest] already
 * round-trips a `Publish` carrying Correlation Data and [de.kempmobil.ktor.mqtt.packet.AuthTest] one
 * carrying Authentication Data, and both passed throughout. **A round trip through the code under test
 * cannot detect this class of defect.** Every assertion below is instead derived from the
 * specification or from hand-written bytes, and none of them calls `byteCount()`.
 *
 * Against a real broker the defect was fatal in both directions. Outbound, the declared property
 * length is two short of the bytes written, so Mosquitto 2.1.2 reports `Unsupported property type` for
 * the first payload byte and drops the connection as malformed. Inbound, a well-formed packet is
 * under-counted and the decode loop reads a payload byte as a property identifier, raising
 * [MalformedPacketException] and killing the subscription.
 */
class BinaryPropertyByteCountTest {

    @Test
    fun `correlation data declares the two byte length prefix it writes`() {
        val correlationData = ByteString(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08))
        val payload = "payload".encodeToByteString()

        val encoded = encode(
            Publish(
                topic = "test/correlated/request".toTopic(),
                correlationData = CorrelationData(correlationData),
                payload = payload
            )
        )
        val properties = encoded.publishPropertyBlock()

        // MQTT 5 section 3.3.2.3.6: one identifier byte, a two byte length prefix, then the data.
        assertEquals(correlationData.size + 3, properties.declared)

        // ...and the declaration matches the bytes actually emitted, which is what a broker checks.
        assertEquals(encoded.size - properties.start - payload.size, properties.declared)
    }

    @Test
    fun `authentication data declares the two byte length prefix it writes`() {
        val authenticationData = ByteString(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))

        val encoded = encode(
            Auth(
                reason = Success,
                authenticationMethod = AuthenticationMethod("SCRAM-SHA-1"),
                authenticationData = AuthenticationData(authenticationData)
            )
        )
        val properties = encoded.authPropertyBlock()

        // The authentication method is a string property, already counted correctly as utf8Size + 3.
        val methodBytes = "SCRAM-SHA-1".length + 3
        assertEquals(methodBytes + authenticationData.size + 3, properties.declared)
        assertEquals(encoded.size - properties.start, properties.declared)
    }

    @Test
    fun `correlation data survives decoding a hand-written publish`() = runTest {
        // A PUBLISH built by hand rather than by the encoder, so the decoder is measured against the
        // specification instead of against our own idea of the wire format.
        val bytes = byteArrayOf(
            0x30,                                     // PUBLISH, QoS 0: no packet identifier follows
            0x0F,                                     // remaining length: 15
            0x00, 0x03, 'a'.code.toByte(), '/'.code.toByte(), 'b'.code.toByte(),
            0x07,                                     // property block: 7 bytes
            0x09, 0x00, 0x04,                         // correlation data, 4 bytes long
            'c'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'r'.code.toByte(),
            'h'.code.toByte(), 'i'.code.toByte()      // payload
        )

        val publish = ByteReadChannel(bytes).readPacket() as Publish

        assertEquals(CorrelationData("corr".encodeToByteString()), publish.correlationData)
        assertEquals("hi".encodeToByteString(), publish.payload)
    }

    @Test
    fun `authentication data survives decoding a hand-written auth`() = runTest {
        val bytes = byteArrayOf(
            0xF0.toByte(),                            // AUTH
            0x0F,                                     // remaining length: 15
            0x00,                                     // reason code: success
            0x0D,                                     // property block: 13 bytes
            0x15, 0x00, 0x04,                         // authentication method, 4 bytes long
            'S'.code.toByte(), 'C'.code.toByte(), 'R'.code.toByte(), 'A'.code.toByte(),
            0x16, 0x00, 0x03,                         // authentication data, 3 bytes long
            0x01, 0x02, 0x03
        )

        val auth = ByteReadChannel(bytes).readPacket() as Auth

        assertEquals(AuthenticationMethod("SCRA"), auth.authenticationMethod)
        assertEquals(AuthenticationData(ByteString(byteArrayOf(0x01, 0x02, 0x03))), auth.authenticationData)
    }

    @Test
    fun `the binary property size holds at both ends of the permitted range`() {
        // writeMqttByteString permits 0..65535 bytes, and the length prefix is written whatever the
        // size -- so an empty value still occupies 3 bytes, and the largest legal one must not be
        // mis-sized by the length prefix either.
        assertEquals(3, publishCarrying(correlationDataOfSize = 0).publishPropertyBlock().declared)
        assertEquals(65_538, publishCarrying(correlationDataOfSize = 65_535).publishPropertyBlock().declared)
    }

    @Test
    fun `an empty correlation data value survives a round trip through a broker-shaped packet`() = runTest {
        // The empty case is the one most likely to be special-cased wrongly by a size calculation,
        // and it must still decode as *present but empty* rather than as absent.
        val bytes = byteArrayOf(
            0x30,                                     // PUBLISH, QoS 0
            0x0B,                                     // remaining length: 11 = topic 5 + vbi 1 + props 3 + payload 2
            0x00, 0x03, 'a'.code.toByte(), '/'.code.toByte(), 'b'.code.toByte(),
            0x03,                                     // property block: 3 bytes
            0x09, 0x00, 0x00,                         // correlation data, zero bytes long
            'h'.code.toByte(), 'i'.code.toByte()      // payload
        )

        val publish = ByteReadChannel(bytes).readPacket() as Publish

        assertEquals(CorrelationData(ByteString()), publish.correlationData)
        assertEquals("hi".encodeToByteString(), publish.payload)
    }

    @Test
    fun `property block length prefix rolls over at 128 bytes`() {
        // A variable byte integer needs a second byte from 128 onwards, so a property block that is
        // mis-sized by two near the boundary also picks the wrong number of length bytes.
        val justUnder = publishCarrying(correlationDataOfSize = 124).publishPropertyBlock()
        assertEquals(127, justUnder.declared)
        assertEquals(1, justUnder.lengthPrefixSize)

        val justOver = publishCarrying(correlationDataOfSize = 125).publishPropertyBlock()
        assertEquals(128, justOver.declared)
        assertEquals(2, justOver.lengthPrefixSize)
    }

    private fun publishCarrying(correlationDataOfSize: Int): ByteArray = encode(
        Publish(
            topic = "t".toTopic(),
            correlationData = CorrelationData(ByteString(ByteArray(correlationDataOfSize))),
            payload = ByteString()
        )
    )

    private fun encode(packet: Packet): ByteArray = Buffer().apply { write(packet) }.readByteArray()

    /**
     * The property block of an encoded packet: the length it declares, the offset of the first
     * property byte, and how many bytes the variable byte integer length itself occupies.
     */
    private class PropertyBlock(val declared: Int, val start: Int, val lengthPrefixSize: Int)

    /**
     * Walks an encoded QoS 0 PUBLISH per MQTT 5 section 3.3: fixed header byte, remaining length,
     * the two-byte-prefixed topic name, then the property block. QoS 0 carries no packet identifier,
     * so nothing sits between the topic and the properties.
     */
    private fun ByteArray.publishPropertyBlock(): PropertyBlock {
        var offset = variableByteIntAt(1).second
        val topicLength = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
        offset += 2 + topicLength
        return propertyBlockAt(offset)
    }

    /**
     * Walks an encoded AUTH per MQTT 5 section 3.15: fixed header byte, remaining length, the reason
     * code, then the property block.
     */
    private fun ByteArray.authPropertyBlock(): PropertyBlock = propertyBlockAt(variableByteIntAt(1).second + 1)

    private fun ByteArray.propertyBlockAt(offset: Int): PropertyBlock {
        val (declared, start) = variableByteIntAt(offset)
        return PropertyBlock(declared = declared, start = start, lengthPrefixSize = start - offset)
    }

    /**
     * Reads a variable byte integer at [index], returning its value and the offset just past it.
     */
    private fun ByteArray.variableByteIntAt(index: Int): Pair<Int, Int> {
        var multiplier = 1
        var value = 0
        var offset = index
        while (true) {
            val byte = this[offset].toInt() and 0xFF
            value += (byte and 0x7F) * multiplier
            offset++
            if (byte and 0x80 == 0) return value to offset
            multiplier *= 128
        }
    }
}
