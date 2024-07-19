package de.kempmobil.ktor.mqtt

public data class ReasonCode(val code: Int, val name: String) {

    public override fun toString(): String {
        return "$code $name"
    }

    public companion object {
        public fun from(code: Byte, defaultSuccessReason: ReasonCode = Success): ReasonCode {
            check(defaultSuccessReason.code == 0) {
                "The default success reason must be one of 'Success', NormalDisconnection' or 'GrantedQoS0'"
            }

            return when (code.toInt() and 0xFF) {
                0 -> defaultSuccessReason
                GrantedQoS1.code -> GrantedQoS1
                GrantedQoS2.code -> GrantedQoS2
                DisconnectWithWillMessage.code -> DisconnectWithWillMessage
                NoMatchingSubscribers.code -> NoMatchingSubscribers
                NoSubscriptionExisted.code -> NoSubscriptionExisted
                ContinueAuthentication.code -> ContinueAuthentication
                ReAuthenticate.code -> ReAuthenticate
                UnspecifiedError.code -> UnspecifiedError
                MalformedPacket.code -> MalformedPacket
                ProtocolError.code -> ProtocolError
                ImplementationSpecificError.code -> ImplementationSpecificError
                UnsupportedProtocolVersion.code -> UnsupportedProtocolVersion
                ClientIdentifierNotValid.code -> ClientIdentifierNotValid
                BadUserNameOrPassword.code -> BadUserNameOrPassword
                NotAuthorized.code -> NotAuthorized
                ServerUnavailable.code -> ServerUnavailable
                ServerBusy.code -> ServerBusy
                Banned.code -> Banned
                ServerShuttingDown.code -> ServerShuttingDown
                BadAuthenticationMethod.code -> BadAuthenticationMethod
                KeepAliveTimeout.code -> KeepAliveTimeout
                SessionTakenOver.code -> SessionTakenOver
                TopicFilterInvalid.code -> TopicFilterInvalid
                TopicNameInvalid.code -> TopicNameInvalid
                PacketIdentifierInUse.code -> PacketIdentifierInUse
                PacketIdentifierNotFound.code -> PacketIdentifierNotFound
                ReceiveMaximumExceeded.code -> ReceiveMaximumExceeded
                TopicAliasInvalid.code -> TopicAliasInvalid
                PacketTooLarge.code -> PacketTooLarge
                MessageRateTooHigh.code -> MessageRateTooHigh
                QuotaExceeded.code -> QuotaExceeded
                AdministrativeAction.code -> AdministrativeAction
                PayloadFormatInvalid.code -> PayloadFormatInvalid
                RetainNotSupported.code -> RetainNotSupported
                QoSNotSupported.code -> QoSNotSupported
                UseAnotherServer.code -> UseAnotherServer
                ServerMoved.code -> ServerMoved
                SharedSubscriptionsNotSupported.code -> SharedSubscriptionsNotSupported
                ConnectionRateExceeded.code -> ConnectionRateExceeded
                MaximumConnectTime.code -> MaximumConnectTime
                SubscriptionIdentifiersNotSupported.code -> SubscriptionIdentifiersNotSupported
                WildcardSubscriptionsNotSupported.code -> WildcardSubscriptionsNotSupported
                else -> throw MalformedPacketException("Unknown reason code: $code")
            }
        }
    }
}

public val Success: ReasonCode = ReasonCode(0, "Success")
public val NormalDisconnection: ReasonCode = ReasonCode(0, "Normal disconnection") // Used only in DISCONNECT packet
public val GrantedQoS0: ReasonCode = ReasonCode(0, "Granted QoS 0")                // Used only in SUBACK packet
public val GrantedQoS1: ReasonCode = ReasonCode(1, "Granted QoS 1")
public val GrantedQoS2: ReasonCode = ReasonCode(2, "Granted QoS 2")
public val DisconnectWithWillMessage: ReasonCode = ReasonCode(4, "Disconnect with Will Message")
public val NoMatchingSubscribers: ReasonCode = ReasonCode(16, "No matching subscribers")
public val NoSubscriptionExisted: ReasonCode = ReasonCode(17, "No subscription existed")
public val ContinueAuthentication: ReasonCode = ReasonCode(24, "Continue authentication")
public val ReAuthenticate: ReasonCode = ReasonCode(25, "Re-authenticate")
public val UnspecifiedError: ReasonCode = ReasonCode(128, "Unspecified error")
public val MalformedPacket: ReasonCode = ReasonCode(129, "Malformed Packet")
public val ProtocolError: ReasonCode = ReasonCode(130, "Protocol Error")
public val ImplementationSpecificError: ReasonCode = ReasonCode(131, "Implementation specific error")
public val UnsupportedProtocolVersion: ReasonCode = ReasonCode(132, "Unsupported Protocol Version")
public val ClientIdentifierNotValid: ReasonCode = ReasonCode(133, "Client Identifier not valid")
public val BadUserNameOrPassword: ReasonCode = ReasonCode(134, "Bad User Name or Password")
public val NotAuthorized: ReasonCode = ReasonCode(135, "Not authorized")
public val ServerUnavailable: ReasonCode = ReasonCode(136, "Server unavailable")
public val ServerBusy: ReasonCode = ReasonCode(137, "Server busy")
public val Banned: ReasonCode = ReasonCode(138, "Banned")
public val ServerShuttingDown: ReasonCode = ReasonCode(139, "Server shutting down")
public val BadAuthenticationMethod: ReasonCode = ReasonCode(140, "Bad authentication method")
public val KeepAliveTimeout: ReasonCode = ReasonCode(141, "Keep Alive timeout")
public val SessionTakenOver: ReasonCode = ReasonCode(142, "Session taken over")
public val TopicFilterInvalid: ReasonCode = ReasonCode(143, "Topic Filter invalid")
public val TopicNameInvalid: ReasonCode = ReasonCode(144, "Topic Name invalid")
public val PacketIdentifierInUse: ReasonCode = ReasonCode(145, "Packet Identifier in use")
public val PacketIdentifierNotFound: ReasonCode = ReasonCode(146, "Packet Identifier not found")
public val ReceiveMaximumExceeded: ReasonCode = ReasonCode(147, "Receive Maximum exceeded")
public val TopicAliasInvalid: ReasonCode = ReasonCode(148, "Topic Alias invalid")
public val PacketTooLarge: ReasonCode = ReasonCode(149, "Packet too large")
public val MessageRateTooHigh: ReasonCode = ReasonCode(150, "Message rate too high")
public val QuotaExceeded: ReasonCode = ReasonCode(151, "Quota exceeded")
public val AdministrativeAction: ReasonCode = ReasonCode(152, "Administrative action")
public val PayloadFormatInvalid: ReasonCode = ReasonCode(153, "Payload format invalid")
public val RetainNotSupported: ReasonCode = ReasonCode(154, "Retain not supported")
public val QoSNotSupported: ReasonCode = ReasonCode(155, "QoS not supported")
public val UseAnotherServer: ReasonCode = ReasonCode(156, "Use another server")
public val ServerMoved: ReasonCode = ReasonCode(157, "Server moved")
public val SharedSubscriptionsNotSupported: ReasonCode = ReasonCode(158, "Shared Subscriptions not supported")
public val ConnectionRateExceeded: ReasonCode = ReasonCode(159, "Connection rate exceeded")
public val MaximumConnectTime: ReasonCode = ReasonCode(160, "Maximum connect time")
public val SubscriptionIdentifiersNotSupported: ReasonCode = ReasonCode(161, "Subscription Identifiers not supported")
public val WildcardSubscriptionsNotSupported: ReasonCode = ReasonCode(162, "Wildcard Subscriptions not supported")