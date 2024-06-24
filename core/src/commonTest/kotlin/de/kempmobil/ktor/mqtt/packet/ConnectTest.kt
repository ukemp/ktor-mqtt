package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.QoS
import de.kempmobil.ktor.mqtt.SessionExpiryInterval
import de.kempmobil.ktor.mqtt.buildWillMessage
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.readVariableByteInt
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConnectTest {

    @Test
    fun `all bytes are written correctly`() {
        val willMessage = buildWillMessage("will-topic") {
            payload = ByteString(byteArrayOf(1, 5, 33))
            properties {
                willDelayInterval = 99
            }
        }

        val connect = Connect(
            isCleanStart = true,
            willMessage = willMessage,
            willOqS = QoS.AT_LEAST_ONCE,
            retainWillMessage = false,
            keepAliveSeconds = 67.toUShort(),
            clientId = "client-id",
            sessionExpiryInterval = SessionExpiryInterval(10),
            userName = "user-name",
            password = "password"
        )

        val reader = buildPacket {
            write(connect)
        }

        // Variable header, this is taken from the MQTT specification, espcially the bit flags, see
        // https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901057
        assertEquals("MQTT", reader.readMqttString())
        assertEquals(5, reader.readByte())             // MQTT version
        assertEquals(206.toByte(), reader.readByte())           // Bits flags should be '11001110' (0xCE)
        assertEquals(67, reader.readShort())           // Keep alive value
        assertEquals(5, reader.readVariableByteInt())  // Properties length
        assertEquals(17, reader.readByte())            // Session Expiry Interval identifier
        assertEquals(10, reader.readInt())             // Session Expiry Interval value

        // Payload
        assertEquals("client-id", reader.readMqttString())
        assertEquals(5, reader.readByte())             // Will message properties length (contains only 1 property)
        assertEquals(24, reader.readByte())            // Will delay interval identifier (24)
        assertEquals(99, reader.readInt())             // Will delay interval value
        assertEquals("will-topic", reader.readMqttString())
        assertEquals(3, reader.readShort())            // Will payload of size 3
        assertEquals(1, reader.readByte())             // Will payload byte 1
        assertEquals(5, reader.readByte())             // Will payload byte 2
        assertEquals(33, reader.readByte())             // Will payload byte 3
        assertEquals("user-name", reader.readMqttString())
        assertEquals("password", reader.readMqttString())

        // End of stream
        assertFalse(reader.canRead())
    }

    @Test
    fun `reading connect packet`() {
        val willMessage = buildWillMessage("will-topic") {
            payload = ByteString(byteArrayOf(1, 5, 33))
            properties {
                willDelayInterval = 99
            }
        }

        val connect = Connect(
            isCleanStart = true,
            willMessage = willMessage,
            willOqS = QoS.AT_LEAST_ONCE,
            retainWillMessage = false,
            keepAliveSeconds = 67.toUShort(),
            clientId = "client-id",
            sessionExpiryInterval = SessionExpiryInterval(10),
            userName = "user-name",
            password = "password"
        )

        val reader = buildPacket {
            write(connect)
        }

        val actual = reader.readConnect()
        assertEquals(connect, actual)
    }
}