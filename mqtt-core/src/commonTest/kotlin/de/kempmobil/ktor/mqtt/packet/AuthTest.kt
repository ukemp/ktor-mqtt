package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.io.bytestring.encodeToByteString
import kotlin.test.Test

class AuthTest {

    @Test
    fun `encode and decode returns same packet`() {
        assertEncodeDecodeOf(Auth(Success, AuthenticationMethod("auth")))
        assertEncodeDecodeOf(
            Auth(
                Success,
                AuthenticationMethod("auth"),
                AuthenticationData("data".encodeToByteString()),
                ReasonString("reason"),
                buildUserProperties { "key" to "prop" })
        )
    }
}