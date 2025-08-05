package de.kempmobil.ktor.mqtt

/**
 * Represents a **Reason Code** as specified in
 * [chapter 2.4](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901031) of the MQTT specification.
 *
 * @property code the decimal reason code value as defined in MQTT
 * @property name the name of this reason code
 * @see Success
 * @see NormalDisconnection
 * @see GrantedQoS0
 * @see GrantedQoS1
 * @see GrantedQoS2
 * @see DisconnectWithWillMessage
 * @see NoMatchingSubscribers
 * @see NoSubscriptionExisted
 * @see ContinueAuthentication
 * @see ReAuthenticate
 * @see UnspecifiedError
 * @see MalformedPacket
 * @see ProtocolError
 * @see ImplementationSpecificError
 * @see UnsupportedProtocolVersion
 * @see ClientIdentifierNotValid
 * @see BadUserNameOrPassword
 * @see NotAuthorized
 * @see ServerUnavailable
 * @see ServerBusy
 * @see Banned
 * @see ServerShuttingDown
 * @see BadAuthenticationMethod
 * @see KeepAliveTimeout
 * @see SessionTakenOver
 * @see TopicFilterInvalid
 * @see TopicNameInvalid
 * @see PacketIdentifierInUse
 * @see PacketIdentifierNotFound
 * @see ReceiveMaximumExceeded
 * @see TopicAliasInvalid
 * @see PacketTooLarge
 * @see MessageRateTooHigh
 * @see QuotaExceeded
 * @see AdministrativeAction
 * @see PayloadFormatInvalid
 * @see RetainNotSupported
 * @see QoSNotSupported
 * @see UseAnotherServer
 * @see ServerMoved
 * @see SharedSubscriptionsNotSupported
 * @see ConnectionRateExceeded
 * @see MaximumConnectTime
 * @see SubscriptionIdentifiersNotSupported
 * @see WildcardSubscriptionsNotSupported
 */
@ConsistentCopyVisibility
public data class ReasonCode internal constructor(val code: Int, val name: String) {

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

    public operator fun compareTo(other: ReasonCode): Int = this.code.compareTo(other.code)
}

/**
 * The Success reason code
 */
public val Success: ReasonCode = ReasonCode(0, "Success")

/**
 * The NormalDisconnection reason code, only used in `DISCONNECT` packets.
 */
public val NormalDisconnection: ReasonCode = ReasonCode(0, "Normal disconnection")

/**
 * The GrantedQoS0 reason code, only used in `SUBACK` packets.
 */
public val GrantedQoS0: ReasonCode = ReasonCode(0, "Granted QoS 0")

/**
 * The GrantedQoS1 reason code.
 */
public val GrantedQoS1: ReasonCode = ReasonCode(1, "Granted QoS 1")

/**
 * The GrantedQoS2 reason code.
 */
public val GrantedQoS2: ReasonCode = ReasonCode(2, "Granted QoS 2")

/**
 * The DisconnectWithWillMessage reason code.
 */
public val DisconnectWithWillMessage: ReasonCode = ReasonCode(4, "Disconnect with Will Message")

/**
 * The NoMatchingSubscribers reason code.
 */
public val NoMatchingSubscribers: ReasonCode = ReasonCode(16, "No matching subscribers")

/**
 * The NoSubscriptionExisted reason code.
 */
public val NoSubscriptionExisted: ReasonCode = ReasonCode(17, "No subscription existed")

/**
 * The ContinueAuthentication reason code.
 */
public val ContinueAuthentication: ReasonCode = ReasonCode(24, "Continue authentication")

/**
 * The ReAuthenticate reason code.
 */
public val ReAuthenticate: ReasonCode = ReasonCode(25, "Re-authenticate")

/**
 * The UnspecifiedError reason code.
 */
public val UnspecifiedError: ReasonCode = ReasonCode(128, "Unspecified error")

/**
 * The MalformedPacket reason code.
 */
public val MalformedPacket: ReasonCode = ReasonCode(129, "Malformed Packet")

/**
 * The ProtocolError reason code.
 */
public val ProtocolError: ReasonCode = ReasonCode(130, "Protocol Error")

/**
 * The ImplementationSpecificError reason code.
 */
public val ImplementationSpecificError: ReasonCode = ReasonCode(131, "Implementation specific error")

/**
 * The UnsupportedProtocolVersion reason code.
 */
public val UnsupportedProtocolVersion: ReasonCode = ReasonCode(132, "Unsupported Protocol Version")

/**
 * The ClientIdentifierNotValid reason code.
 */
public val ClientIdentifierNotValid: ReasonCode = ReasonCode(133, "Client Identifier not valid")

/**
 * The BadUserNameOrPassword reason code.
 */
public val BadUserNameOrPassword: ReasonCode = ReasonCode(134, "Bad User Name or Password")

/**
 * The NotAuthorized reason code.
 */
public val NotAuthorized: ReasonCode = ReasonCode(135, "Not authorized")

/**
 * The ServerUnavailable reason code.
 */
public val ServerUnavailable: ReasonCode = ReasonCode(136, "Server unavailable")

/**
 * The ServerBusy reason code.
 */
public val ServerBusy: ReasonCode = ReasonCode(137, "Server busy")

/**
 * The Banned reason code.
 */
public val Banned: ReasonCode = ReasonCode(138, "Banned")

/**
 * The ServerShuttingDown reason code.
 */
public val ServerShuttingDown: ReasonCode = ReasonCode(139, "Server shutting down")

/**
 * The BadAuthenticationMethod reason code.
 */
public val BadAuthenticationMethod: ReasonCode = ReasonCode(140, "Bad authentication method")

/**
 * The KeepAliveTimeout reason code.
 */
public val KeepAliveTimeout: ReasonCode = ReasonCode(141, "Keep Alive timeout")

/**
 * The SessionTakenOver reason code.
 */
public val SessionTakenOver: ReasonCode = ReasonCode(142, "Session taken over")

/**
 * The TopicFilterInvalid reason code.
 */
public val TopicFilterInvalid: ReasonCode = ReasonCode(143, "Topic Filter invalid")

/**
 * The TopicNameInvalid reason code.
 */
public val TopicNameInvalid: ReasonCode = ReasonCode(144, "Topic Name invalid")

/**
 * The PacketIdentifierInUse reason code.
 */
public val PacketIdentifierInUse: ReasonCode = ReasonCode(145, "Packet Identifier in use")

/**
 * The PacketIdentifierNotFound reason code.
 */
public val PacketIdentifierNotFound: ReasonCode = ReasonCode(146, "Packet Identifier not found")

/**
 * The ReceiveMaximumExceeded reason code.
 */
public val ReceiveMaximumExceeded: ReasonCode = ReasonCode(147, "Receive Maximum exceeded")

/**
 * The TopicAliasInvalid reason code.
 */
public val TopicAliasInvalid: ReasonCode = ReasonCode(148, "Topic Alias invalid")

/**
 * The PacketTooLarge reason code.
 */
public val PacketTooLarge: ReasonCode = ReasonCode(149, "Packet too large")

/**
 * The MessageRateTooHigh reason code.
 */
public val MessageRateTooHigh: ReasonCode = ReasonCode(150, "Message rate too high")

/**
 * The QuotaExceeded reason code.
 */
public val QuotaExceeded: ReasonCode = ReasonCode(151, "Quota exceeded")

/**
 * The AdministrativeAction reason code.
 */
public val AdministrativeAction: ReasonCode = ReasonCode(152, "Administrative action")

/**
 * The AdministrativeAction reason code.
 */
public val PayloadFormatInvalid: ReasonCode = ReasonCode(153, "Payload format invalid")

/**
 * The RetainNotSupported reason code.
 */
public val RetainNotSupported: ReasonCode = ReasonCode(154, "Retain not supported")

/**
 * The QoSNotSupported reason code.
 */
public val QoSNotSupported: ReasonCode = ReasonCode(155, "QoS not supported")

/**
 * The UseAnotherServer reason code.
 */
public val UseAnotherServer: ReasonCode = ReasonCode(156, "Use another server")

/**
 * The ServerMoved reason code.
 */
public val ServerMoved: ReasonCode = ReasonCode(157, "Server moved")

/**
 * The SharedSubscriptionsNotSupported reason code.
 */
public val SharedSubscriptionsNotSupported: ReasonCode = ReasonCode(158, "Shared Subscriptions not supported")

/**
 * The ConnectionRateExceeded reason code.
 */
public val ConnectionRateExceeded: ReasonCode = ReasonCode(159, "Connection rate exceeded")

/**
 * The MaximumConnectTime reason code.
 */
public val MaximumConnectTime: ReasonCode = ReasonCode(160, "Maximum connect time")

/**
 * The SubscriptionIdentifiersNotSupported reason code.
 */
public val SubscriptionIdentifiersNotSupported: ReasonCode = ReasonCode(161, "Subscription Identifiers not supported")

/**
 * The WildcardSubscriptionsNotSupported reason code.
 */
public val WildcardSubscriptionsNotSupported: ReasonCode = ReasonCode(162, "Wildcard Subscriptions not supported")