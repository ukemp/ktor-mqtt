package de.kempmobil.ktor.mqtt

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReceiveMaximumTest {

    @Test
    fun `zero not an allowed value`() {
        assertFailsWith<MalformedPacketException> { ReceiveMaximum(0.toUShort()) }
    }
}