package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DisconnectTest {

    @Test
    fun `encode and decode returns same packet`() = runTest {
        assertEncodeDecode(Disconnect(NormalDisconnection))
        assertEncodeDecode(
            Disconnect(
                NormalDisconnection,
                SessionExpiryInterval(60u),
                ReasonString("reason"),
                buildUserProperties { "user" to "value" })
        )
    }

    @Test
    fun `constructor fails when reason code is Success or GrantedQoS0`() {
        Disconnect(NormalDisconnection)
        Disconnect(DisconnectWithWillMessage)
        Disconnect(UnspecifiedError)

        assertFailsWith<MalformedPacketException> { Disconnect(Success) }
        assertFailsWith<MalformedPacketException> { Disconnect(GrantedQoS0) }
    }
}