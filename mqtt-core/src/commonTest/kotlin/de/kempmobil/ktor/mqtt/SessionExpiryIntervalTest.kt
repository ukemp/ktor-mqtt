package de.kempmobil.ktor.mqtt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class SessionExpiryIntervalTest {

    @Test
    fun `duration computed correctly for values greater than Int_MAX_VALUE`() {
        val maxIntPlusOne = 2147483648
        val expiry = SessionExpiryInterval(maxIntPlusOne.toUInt())
        assertEquals(maxIntPlusOne, expiry.toDuration().inWholeSeconds)
    }

    @Test
    fun `duration computed correctly for infinite value`() {
        val expiry = SessionExpiryInterval(UInt.MAX_VALUE)
        assertEquals(Duration.INFINITE, expiry.toDuration())
    }
}