package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readUShort
import kotlinx.io.writeUShort

public data class Suback(
    override val packetIdentifier: UShort,
    val reasons: List<ReasonCode>,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
) : AbstractPacket(PacketType.SUBACK), PacketIdentifierPacket {

    init {
        malformedWhen(reasons.isEmpty()) { "Reason codes must not be empty in SUBACK" }
        malformedWhen(reasons.contains(Success)) { "Reason code 'Success' is not allowed for SUBACK" }
    }
}

/**
 * Returns `true` when this SUBACK packet contains a reason code which not indicates a success.
 */
public val Suback.hasFailure: Boolean
    get() = reasons.any { it.code > GrantedQoS2.code }

internal fun Sink.write(suback: Suback) {
    with(suback) {
        writeUShort(packetIdentifier)
        writeProperties(reasonString, *userProperties.asArray)

        // Payload
        reasons.forEach {
            writeByte(it.code.toByte())
        }
    }
}

internal fun Source.readSuback(): Suback {
    val packetIdentifier = readUShort()
    val properties = readProperties()
    val reasons = buildList {
        while (!exhausted()) {
            add(ReasonCode.from(readByte(), defaultSuccessReason = GrantedQoS0))
        }
    }

    return Suback(
        packetIdentifier = packetIdentifier,
        reasonString = properties.singleOrNull<ReasonString>(),
        userProperties = UserProperties.from(properties),
        reasons = reasons
    )
}