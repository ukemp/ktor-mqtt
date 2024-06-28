package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.readMqttString
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString

public data class Publish(
    val isDupMessage: Boolean = false,
    val qoS: QoS = QoS.AT_MOST_ONCE,
    val isRetainMessage: Boolean = false,
    val packetIdentifier: UShort? = null,
    val topicName: String,
    val payloadFormatIndicator: PayloadFormatIndicator? = null,
    val messageExpiryInterval: MessageExpiryInterval? = null,
    val topicAlias: TopicAlias? = null,
    val responseTopic: ResponseTopic? = null,
    val correlationData: CorrelationData? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
    val subscriptionIdentifier: SubscriptionIdentifier? = null,
    val contentType: ContentType? = null,
    val payload: ByteString
) : AbstractPacket(PacketType.PUBLISH) {

    init {
        wellFormedWhen(topicName.isNotBlank() || topicAlias != null) {
            "Either a topic name or a topic alias must be present in a PUBLISH paket"
        }
        wellFormedWhen((!qoS.requiresPacketIdentifier) || packetIdentifier != null) {
            "For $qoS a packet identifier must be present"
        }
    }

    override val headerFlags: Int
        get() {
            var bits = if (isRetainMessage) 1 else 0
            bits = bits or (qoS.value shl 1)
            if (isDupMessage) bits = bits or (1 shl 3)
            return bits
        }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun BytePacketBuilder.write(publish: Publish) {
    with(publish) {
        writeMqttString(topicName)
        if (qoS.requiresPacketIdentifier) {
            writeUShort(packetIdentifier!!)
        }
        writeProperties(
            payloadFormatIndicator,
            messageExpiryInterval,
            topicAlias,
            responseTopic,
            correlationData,
            *userProperties.asArray,
            subscriptionIdentifier,
            contentType
        )
        writeFully(payload.toByteArray())
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun ByteReadPacket.readPublish(headerFlags: Int): Publish {
    val qoS = headerFlags.qoS
    val topicName = readMqttString()
    val packetIdentifier = if (qoS.requiresPacketIdentifier) {
        readUShort()
    } else {
        null
    }
    val properties = readProperties()
    val payload = ByteArray(remaining.toInt())
    readFully(payload)

    return Publish(
        isDupMessage = headerFlags.isDupMessage,
        qoS = qoS,
        isRetainMessage = headerFlags.isRetainMessage,
        packetIdentifier = packetIdentifier,
        topicName = topicName,
        payloadFormatIndicator = properties.singleOrNull<PayloadFormatIndicator>(),
        messageExpiryInterval = properties.singleOrNull<MessageExpiryInterval>(),
        topicAlias = properties.singleOrNull<TopicAlias>(),
        responseTopic = properties.singleOrNull<ResponseTopic>(),
        correlationData = properties.singleOrNull<CorrelationData>(),
        userProperties = UserProperties.from(properties),
        subscriptionIdentifier = properties.singleOrNull<SubscriptionIdentifier>(),
        contentType = properties.singleOrNull<ContentType>(),
        payload = ByteString(payload)
    )
}

private val QoS.requiresPacketIdentifier: Boolean
    get() = this.value > 0

private val Int.qoS: QoS
    get() = QoS.from(((this and 6) shr 1))

private val Int.isDupMessage: Boolean
    get() = ((this and 8) shr 3) != 0

private val Int.isRetainMessage: Boolean
    get() = (this and 1) != 0