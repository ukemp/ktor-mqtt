package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.buildUserProperties
import de.kempmobil.ktor.mqtt.topics
import kotlin.test.Test

class UnsubscribeTest {

    @Test
    fun `encode and decode returns same packet`() {
        assertEncodeDecodeOf(Unsubscribe(6677u, topics("test/topic")))
        assertEncodeDecodeOf(
            Unsubscribe(
                6677u,
                topics(
                    "test/topic/1",
                    "test/topic/2",
                    "test/topic/3"
                ),
                buildUserProperties { "key" to "value" })
        )
    }
}