package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.NotAuthorized
import de.kempmobil.ktor.mqtt.ReasonString
import de.kempmobil.ktor.mqtt.Success
import de.kempmobil.ktor.mqtt.buildUserProperties
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UnsubackTest {

    @Test
    fun `encode and decode returns same packet`() = runTest {
        assertEncodeDecode(Unsuback(UShort.MAX_VALUE, listOf(Success)))
        assertEncodeDecode(
            Unsuback(
                1u,
                listOf(Success, NotAuthorized),
                ReasonString("reason"),
                buildUserProperties { "key" to "value" })
        )
    }
}