package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SubscribeTest {

    @Test
    fun `encode and decode returns same packet`() = runTest {
        assertEncodeDecode(Subscribe(1024u, buildFilterList { add("test/topic") }))
        assertEncodeDecode(
            Subscribe(
                packetIdentifier = 1024u,
                filters = buildFilterList {
                    add("test/topic/1")
                    add("test/topic/2", QoS.EXACTLY_ONE, true, true, RetainHandling.DO_NOT_SEND)
                },
                subscriptionIdentifier = SubscriptionIdentifier(8888),
                userProperties = buildUserProperties { "key" to "value" }
            )
        )
    }
}