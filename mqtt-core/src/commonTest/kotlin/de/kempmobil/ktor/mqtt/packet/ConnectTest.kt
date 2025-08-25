package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.readVariableByteInt
import io.ktor.utils.io.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.readUInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.days

class ConnectTest {

    @Test
    fun `encode and decode returns same packet`() = runTest {
        val willMessage = buildWillMessage("will/topic") {
            payload("will payload")
        }
        assertEncodeDecodeOf(
            Connect(
                true,
                willMessage,
                QoS.AT_LEAST_ONCE,
                false,
                60u,
                "client"
            )
        )
        assertEncodeDecodeOf(
            Connect(
                false,
                willMessage,
                QoS.EXACTLY_ONE,
                false,
                60u,
                "client",
                "username",
                "password123",
                SessionExpiryInterval(60u),
                ReceiveMaximum(150u),
                MaximumPacketSize(3000u),
                TopicAliasMaximum(200u),
                RequestResponseInformation(true),
                RequestProblemInformation(true),
                buildUserProperties { "key" to "prop" },
                AuthenticationMethod("auth"),
                AuthenticationData("123".encodeToByteString())
            )
        )
    }

    @Test
    fun `will QoS must be zero when will message is null`() {
        val willMessage = buildWillMessage("will/topic") {
            payload("will payload")
        }

        // Must not fail:
        Connect(true, null, QoS.AT_MOST_ONCE, false, 60u, "client")
        Connect(true, willMessage, QoS.AT_MOST_ONCE, false, 60u, "client")
        Connect(true, willMessage, QoS.AT_LEAST_ONCE, false, 60u, "client")
        Connect(true, willMessage, QoS.EXACTLY_ONE, false, 60u, "client")

        assertFailsWith<MalformedPacketException> {
            Connect(true, null, QoS.AT_LEAST_ONCE, false, 60u, "client")
        }
        assertFailsWith<MalformedPacketException> {
            Connect(true, null, QoS.EXACTLY_ONE, false, 60u, "client")
        }
    }

    @Test
    fun `will message cannot be null when retain will message is true`() {
        assertFailsWith<MalformedPacketException> {
            Connect(true, null, QoS.AT_MOST_ONCE, true, 60u, "client")
        }
    }

    @Test
    fun `all bytes are written correctly`() {
        val willMessage = buildWillMessage("will-topic") {
            payload(ByteString(byteArrayOf(1, 5, 33)))
            properties {
                willDelayInterval = 1.days
            }
        }

        val connect = Connect(
            isCleanStart = true,
            willMessage = willMessage,
            willOqS = QoS.AT_LEAST_ONCE,
            retainWillMessage = false,
            keepAliveSeconds = 67.toUShort(),
            clientId = "client-id",
            sessionExpiryInterval = SessionExpiryInterval(10u),
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
        assertEquals(1.days.inWholeSeconds.toUInt(), reader.readUInt())             // Will delay interval value
        assertEquals("will-topic", reader.readMqttString())
        assertEquals(3, reader.readShort())            // Will payload of size 3
        assertEquals(1, reader.readByte())             // Will payload byte 1
        assertEquals(5, reader.readByte())             // Will payload byte 2
        assertEquals(33, reader.readByte())             // Will payload byte 3
        assertEquals("user-name", reader.readMqttString())
        assertEquals("password", reader.readMqttString())

        // End of stream
        assertFalse(!reader.exhausted())
    }

    @Test
    fun `reading connect packet`() {
        val willMessage = buildWillMessage("will-topic") {
            payload(ByteString(byteArrayOf(1, 5, 33)))
            properties {
                willDelayInterval = 1.days

                userProperties {
                    "user" to "value1"
                    "user" to "value2"
                }
            }
        }

        val connect = Connect(
            isCleanStart = true,
            willMessage = willMessage,
            willOqS = QoS.AT_LEAST_ONCE,
            retainWillMessage = false,
            keepAliveSeconds = 67.toUShort(),
            clientId = "client-id",
            sessionExpiryInterval = SessionExpiryInterval(10u),
            userName = "user-name",
            password = "password"
        )

        val reader = buildPacket {
            write(connect)
        }

        val actual = reader.readConnect()
        assertEquals(connect, actual)

        println(actual)
    }
}