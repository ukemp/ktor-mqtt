package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*

internal data class Unsubscribe(
    val userProperties: UserProperties = UserProperties.EMPTY,
    val topics: List<Topic>
) : AbstractPacket(PacketType.UNSUBSCRIBE) {

    init {
        wellFormedWhen(topics.isNotEmpty()) { "Empty topic list in UNSUBSCRIBE" }
    }

    override val headerFlags = 2
}

internal fun BytePacketBuilder.write(unsubscribe: Unsubscribe) {
    with(unsubscribe) {
        writeProperties(*userProperties.asArray)

        // Payload
        topics.forEach {
            writeMqttString(it.name)
        }
    }
}

internal fun ByteReadPacket.readUnsubscribe(): Unsubscribe {
    val properties = readProperties()
    val topics = buildList {
        while (canRead()) {
            add(Topic(readMqttString()))
        }
    }
    return Unsubscribe(UserProperties.from(properties), topics)
}