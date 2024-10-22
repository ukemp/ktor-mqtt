package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.Topic
import de.kempmobil.ktor.mqtt.buildUserProperties
import kotlin.test.Test

class UnsubscribeTest {

    @Test
    fun `encode and decode returns same packet`() {
        assertEncodeDecodeOf(Unsubscribe(6677u, listOf(Topic("test/topic"))))
        assertEncodeDecodeOf(
            Unsubscribe(
                6677u,
                listOf(
                    Topic("test/topic/1"),
                    Topic("test/topic/2"),
                    Topic("test/topic/3")
                ),
                buildUserProperties { "key" to "value" })
        )
    }
}