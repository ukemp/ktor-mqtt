package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.QoS
import de.kempmobil.ktor.mqtt.SessionExpiryInterval
import de.kempmobil.ktor.mqtt.buildWillMessage
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.readVariableByteInt
import io.ktor.utils.io.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectTest {

    @Test
    fun `header is written with proper values`() {
        val connect = Connect(
            isCleanStart = true,
            willMessage = buildWillMessage("will-topic") { },
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

        // Variable header
        assertEquals("MQTT", reader.readMqttString())
        assertEquals(5, reader.readByte())             // MQTT version
        assertEquals(206.toByte(), reader.readByte())           // Bits flags should be '11001110' (0xCE)
        assertEquals(67, reader.readShort())           // Keep alive value
        assertEquals(5, reader.readVariableByteInt())  // Properties length
        assertEquals(17, reader.readByte())            // Session Expiry Interval identifier
        assertEquals(10, reader.readInt())             // Session Expiry Interval value

        // Payload
        assertEquals("client-id", reader.readMqttString())
    }
}