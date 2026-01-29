package de.kempmobil.ktor.mqtt

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MaximumPacketSizeTest {

    @Test
    fun `zero not an allowed value`() {
        assertFailsWith<MalformedPacketException> { MaximumPacketSize(0.toUInt()) }
    }
}