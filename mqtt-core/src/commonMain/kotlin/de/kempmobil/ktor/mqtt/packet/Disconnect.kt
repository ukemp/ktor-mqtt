package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.io.Sink
import kotlinx.io.Source

public data class Disconnect(
    val reason: ReasonCode,
    val sessionExpiryInterval: SessionExpiryInterval? = null,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY,
    val serverReference: ServerReference? = null,
) : AbstractPacket(PacketType.DISCONNECT)

internal fun Sink.write(disconnect: Disconnect) {
    with(disconnect) {
        writeByte(reason.code.toByte())
        writeProperties(
            sessionExpiryInterval,
            reasonString,
            serverReference,
            *userProperties.asArray
        )
    }
}

internal fun Source.readDisconnect(): Disconnect {
    val reason = ReasonCode.from(readByte(), defaultSuccessReason = NormalDisconnection)

    val properties = if (!exhausted()) {
        readProperties()
    } else {
        emptyList()
    }

    return Disconnect(
        reason = reason,
        sessionExpiryInterval = properties.singleOrNull<SessionExpiryInterval>(),
        reasonString = properties.singleOrNull<ReasonString>(),
        userProperties = UserProperties.from(properties),
        serverReference = properties.singleOrNull<ServerReference>()
    )
}