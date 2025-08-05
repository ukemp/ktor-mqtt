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
) : AbstractPacket(PacketType.DISCONNECT) {

    init {
        malformedWhen(reason == Success || reason == GrantedQoS0) {
            "Only 'NormalDisconnection' is an allowed reason code for successful disconnection"
        }
    }
}

internal fun Sink.write(disconnect: Disconnect) {
    with(disconnect) {
        writeByte(reason.code.toByte())
        // For Disconnect, there is no need to write the properties length bytes, in case there are no properties:
        if (sessionExpiryInterval != null || reasonString != null || serverReference != null || userProperties.isNotEmpty()) {
            writeProperties(
                sessionExpiryInterval,
                reasonString,
                serverReference,
                *userProperties.asArray
            )
        }
    }
}

internal fun Source.readDisconnect(remainingLength: Int): Disconnect {
    return if (remainingLength == 0) {
        Disconnect(NormalDisconnection)
    } else {
        val reason = ReasonCode.from(readByte(), defaultSuccessReason = NormalDisconnection)

        if (remainingLength == 1) {
            Disconnect(reason)
        } else {
            val properties = readProperties()
            Disconnect(
                reason = reason,
                sessionExpiryInterval = properties.singleOrNull<SessionExpiryInterval>(),
                reasonString = properties.singleOrNull<ReasonString>(),
                userProperties = UserProperties.from(properties),
                serverReference = properties.singleOrNull<ServerReference>()
            )
        }
    }
}