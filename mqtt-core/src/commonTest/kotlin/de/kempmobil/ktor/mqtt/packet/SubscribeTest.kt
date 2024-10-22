package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlin.test.Test

class SubscribeTest {

    @Test
    fun `encode and decode returns same packet`() {
        assertEncodeDecodeOf(Subscribe(1024u, buildFilterList { add("test/topic") }))
        assertEncodeDecodeOf(
            Subscribe(
                packetIdentifier = 1024u,
                filters = buildFilterList {
                    add("test/topic/1")
                    add("test/topic/2", QoS.EXACTLY_ONE, true, true, RetainHandling.DO_NOT_SEND)
                    add("test/topic/3", QoS.AT_LEAST_ONCE, false, true, RetainHandling.SEND_IF_NOT_EXISTS)
                },
                subscriptionIdentifier = SubscriptionIdentifier(8888),
                userProperties = buildUserProperties { "key" to "value" }
            )
        )
    }
}