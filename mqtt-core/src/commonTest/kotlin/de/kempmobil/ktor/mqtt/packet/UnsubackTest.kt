package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.NotAuthorized
import de.kempmobil.ktor.mqtt.ReasonString
import de.kempmobil.ktor.mqtt.Success
import de.kempmobil.ktor.mqtt.buildUserProperties
import kotlin.test.Test

class UnsubackTest {

    @Test
    fun `encode and decode returns same packet`() {
        assertEncodeDecodeOf(Unsuback(UShort.MAX_VALUE, listOf(Success)))
        assertEncodeDecodeOf(
            Unsuback(
                1u,
                listOf(Success, NotAuthorized),
                ReasonString("reason"),
                buildUserProperties { "key" to "value" })
        )
    }
}