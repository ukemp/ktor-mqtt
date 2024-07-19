package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*

public class Subscribe(
    public override val packetIdentifier: UShort,
    public val filters: List<TopicFilter>,
    public val subscriptionIdentifier: SubscriptionIdentifier?,
    public val userProperties: UserProperties = UserProperties.EMPTY,
) : AbstractPacket(PacketType.SUBSCRIBE), PacketIdentifierPacket {

    init {
        wellFormedWhen(filters.isNotEmpty()) { "Empty list of topic filters for SUBSCRIBE " }
    }

    override val headerFlags: Int = 2
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun BytePacketBuilder.write(subscribe: Subscribe) {
    with(subscribe) {
        writeUShort(subscribe.packetIdentifier)
        writeProperties(
            subscriptionIdentifier,
            *userProperties.asArray
        )

        // Filter are written as payload
        filters.forEach {
            writeMqttString(it.filter.name)
            writeByte(it.subscriptionOptions.bits)
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun ByteReadPacket.readSubscribe(): Subscribe {
    val packetIdentifier = readUShort()
    val properties = readProperties()
    val filters = buildList {
        while (canRead()) {
            val filter = readMqttString()
            val options = readByte().toSubscriptionOptions()
            add(TopicFilter(Topic(filter), options))
        }
    }

    return Subscribe(
        packetIdentifier = packetIdentifier,
        filters = filters,
        subscriptionIdentifier = properties.singleOrNull<SubscriptionIdentifier>(),
        userProperties = UserProperties.from(properties)
    )
}

private val SubscriptionOptions.bits: Byte
    get() {
        var bits = qoS.value
        if (isNoLocal) bits = bits or 4
        if (retainAsPublished) bits = bits or 8
        bits = bits or (retainHandling.value shl 4)

        return bits.toByte()
    }

private fun Byte.toSubscriptionOptions(): SubscriptionOptions {
    val bits = toInt()
    val qoS = QoS.from(bits and 3)
    val isNoLocal = (bits and 4) shr 2 != 0
    val retainAsPublished = (bits and 8) shr 3 != 0

    return SubscriptionOptions(qoS, isNoLocal, retainAsPublished, RetainHandling.from((bits and 48) shr 4))
}