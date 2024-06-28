package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import io.ktor.utils.io.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnackTest {

    @Test
    fun `all bytes are written correctly`() {
        val userProperties = buildUserProperties {
            +("prop1" to "value1")
            +("prop2" to "value2")
        }
        val connack = Connack(
            isSessionPresent = true,
            reason = ReAuthenticate,
            receiveMaximum = ReceiveMaximum(27),
            serverKeepAlive = ServerKeepAlive(99),
            userProperties = userProperties
        )

        val reader = buildPacket {
            write(connack)
        }

        val actual = reader.readConnack()

        assertTrue(actual.isSessionPresent)
        assertEquals(ReAuthenticate, actual.reason)
        assertEquals(27, actual.receiveMaximum?.value)
        assertEquals(99, actual.serverKeepAlive?.value)
        assertEquals(userProperties, actual.userProperties)
        assertNull(actual.sessionExpiryInterval)
        assertNull(actual.maximumQoS)
        assertNull(actual.retainAvailable)
        assertNull(actual.maximumPacketSize)
        assertNull(actual.assignedClientIdentifier)
        assertNull(actual.topicAliasMaximum)
        assertNull(actual.reasonString)
        assertNull(actual.wildcardSubscriptionAvailable)
        assertNull(actual.subscriptionIdentifierAvailable)
        assertNull(actual.sharedSubscriptionAvailable)
        assertNull(actual.responseInformation)
        assertNull(actual.serverReference)
        assertNull(actual.authenticationMethod)
        assertNull(actual.authenticationData)
    }
}