package de.kempmobil.ktor.mqtt.packet

import de.kempmobil.ktor.mqtt.*
import kotlinx.io.Sink
import kotlinx.io.Source

public data class Auth(
    val reason: ReasonCode,
    val authenticationMethod: AuthenticationMethod,
    val authenticationData: AuthenticationData? = null,
    val reasonString: ReasonString? = null,
    val userProperties: UserProperties = UserProperties.EMPTY
) : AbstractPacket(PacketType.AUTH) {

    init {
        wellFormedWhen(
            when (reason.code) {
                Success.code -> true
                ContinueAuthentication.code -> true
                ReAuthenticate.code -> true
                else -> false
            }
        ) { "Invalid reason code for AUTH: $reason" }
    }
}

internal fun Sink.write(auth: Auth) {
    with(auth) {
        writeByte(reason.code.toByte())
        writeProperties(
            authenticationMethod,
            authenticationData,
            reasonString,
            *userProperties.asArray
        )
    }
}

internal fun Source.readAuth(): Auth {
    val reason = ReasonCode.from(readByte())
    val properties = readProperties()

    return Auth(
        reason = reason,
        authenticationMethod = properties.single<AuthenticationMethod>(),
        authenticationData = properties.singleOrNull<AuthenticationData>(),
        reasonString = properties.singleOrNull<ReasonString>(),
        userProperties = UserProperties.from(properties)
    )
}