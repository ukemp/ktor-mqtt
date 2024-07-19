package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*

public data class Unsubscribe(
    public override val packetIdentifier: UShort,
    public val topics: List<Topic>,
    public val userProperties: UserProperties = UserProperties.EMPTY
) : PacketIdentifierPacket(PacketType.UNSUBSCRIBE) {

    init {
        wellFormedWhen(topics.isNotEmpty()) { "Empty topic list in UNSUBSCRIBE" }
    }

    override val headerFlags: Int = 2
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun BytePacketBuilder.write(unsubscribe: Unsubscribe) {
    with(unsubscribe) {
        writeUShort(packetIdentifier)
        writeProperties(*userProperties.asArray)

        // Payload
        topics.forEach {
            writeMqttString(it.name)
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun ByteReadPacket.readUnsubscribe(): Unsubscribe {
    val packetIdentifier = readUShort()
    val properties = readProperties()
    val topics = buildList {
        while (canRead()) {
            add(Topic(readMqttString()))
        }
    }
    return Unsubscribe(packetIdentifier, topics, UserProperties.from(properties))
}