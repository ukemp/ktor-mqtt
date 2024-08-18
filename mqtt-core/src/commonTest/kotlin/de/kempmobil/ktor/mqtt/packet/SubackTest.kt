package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SubackTest {

    @Test
    fun `encode and decode returns same packet`() = runTest {
        assertEncodeDecode(Suback(42u, listOf(GrantedQoS0)))
        assertEncodeDecode(
            Suback(
                42u,
                listOf(GrantedQoS1, GrantedQoS2),
                ReasonString("reason"),
                buildUserProperties { "key" to "value" })
        )
    }
}