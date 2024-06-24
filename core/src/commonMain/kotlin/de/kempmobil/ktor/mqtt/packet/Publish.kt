package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import de.kempmobil.ktor.mqtt.util.writeMqttString
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString

public data class Publish(
    public val isDupMessage: Boolean,
    public val qoS: QoS,
    public val isRetainMessage: Boolean,
    public val packetIdentifier: UShort?,
    public val topicName: String,
    public val payloadFormatIndicator: PayloadFormatIndicator? = null,
    public val messageExpiryInterval: MessageExpiryInterval? = null,
    public val topicAlias: TopicAlias? = null,
    public val responseTopic: ResponseTopic? = null,
    public val correlationData: CorrelationData? = null,
    public val userProperties: UserProperties = UserProperties.EMPTY,
    public val subscriptionIdentifier: SubscriptionIdentifier? = null,
    public val contentType: ContentType? = null,
    public val payload: ByteString
) : AbstractPacket(PacketType.PUBLISH) {

    init {
        wellFormedWhen(topicName.isNotBlank() || topicAlias != null) {
            "Either a topic name or a topic alias must be present in a PUBLISH paket"
        }
        wellFormedWhen(qoS.value == 0 || packetIdentifier != null) {
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
        if (qoS.value > 0) {
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