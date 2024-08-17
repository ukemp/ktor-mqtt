package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readUShort
import kotlinx.io.writeUShort

public data class Unsubscribe(
    public override val packetIdentifier: UShort,
    public val topics: List<Topic>,
    public val userProperties: UserProperties = UserProperties.EMPTY
) : AbstractPacket(PacketType.UNSUBSCRIBE), PacketIdentifierPacket {

    init {
        wellFormedWhen(topics.isNotEmpty()) { "Empty topic list in UNSUBSCRIBE" }
    }

    override val headerFlags: Int = 2
}

internal fun Sink.write(unsubscribe: Unsubscribe) {
    with(unsubscribe) {
        writeUShort(packetIdentifier)
        writeProperties(*userProperties.asArray)

        // Payload
        topics.forEach {
            writeMqttString(it.name)
        }
    }
}

internal fun Source.readUnsubscribe(): Unsubscribe {
    val packetIdentifier = readUShort()
    val properties = readProperties()
    val topics = buildList {
        while (!exhausted()) {
            add(Topic(readMqttString()))
        }
    }
    return Unsubscribe(packetIdentifier, topics, UserProperties.from(properties))
}