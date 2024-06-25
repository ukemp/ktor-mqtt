package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.SubscriptionIdentifier
import de.kempmobil.ktor.mqtt.TopicFilter
import de.kempmobil.ktor.mqtt.UserProperties
import de.kempmobil.ktor.mqtt.wellFormedWhen

internal class Subscribe(
    val packetIdentifier: UShort,
    val filters: List<TopicFilter>,
    val subscriptionIdentifier: SubscriptionIdentifier?,
    val userProperties: UserProperties = UserProperties.EMPTY,
) : AbstractPacket(PacketType.SUBSCRIBE) {

    init {
        wellFormedWhen(filters.isNotEmpty()) { "Empty list of topic filters for SUBSCRIBE " }
    }

    override val headerFlags = 2
}