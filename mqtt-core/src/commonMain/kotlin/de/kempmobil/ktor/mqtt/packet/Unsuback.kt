package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readUShort
import kotlinx.io.writeUShort

public data class Unsuback(
    override val packetIdentifier: UShort,
    val reasons: List<ReasonCode>,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY
) : AbstractPacket(PacketType.UNSUBACK), PacketIdentifierPacket {

    init {
        wellFormedWhen(reasons.isNotEmpty()) { "Reason codes must not be empty in UNSUBACK" }
    }
}

/**
 * Returns `true` when all reason codes of this UNSUBACK are either [Success] or [NoSubscriptionExisted].
 */
public val Unsuback.isUnsubscribed: Boolean
    get() = reasons.all { it.code == Success.code || it.code == NoSubscriptionExisted.code }

internal fun Sink.write(unsuback: Unsuback) {
    with(unsuback) {
        writeUShort(packetIdentifier)
        writeProperties(reasonString, *userProperties.asArray)

        // Payload
        reasons.forEach {
            writeByte(it.code.toByte())
        }
    }
}


internal fun Source.readUnsuback(): Unsuback {
    val packetIdentifier = readUShort()
    val properties = readProperties()
    val reasons = buildList {
        while (!exhausted()) {
            add(ReasonCode.from(readByte()))
        }
    }

    return Unsuback(
        packetIdentifier = packetIdentifier,
        reasonString = properties.singleOrNull<ReasonString>(),
        userProperties = UserProperties.from(properties),
        reasons = reasons
    )
}
