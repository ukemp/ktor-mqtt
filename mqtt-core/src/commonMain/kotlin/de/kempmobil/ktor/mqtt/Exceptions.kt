package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Publish

public open class MqttException internal constructor(message: String? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)

public class MalformedPacketException(message: String? = null) :
    MqttException(message)

public class ConnectionException(message: String? = null, cause: Throwable? = null) :
    MqttException(message = message, cause = cause)

public class TimeoutException(message: String) :
    MqttException(message)

public class TopicAliasException(message: String?) :
    MqttException(message)

/**
 * Indicates that the handshake procedure for [QoS.AT_LEAST_ONCE] or for [QoS.EXACTLY_ONE] has failed for the specified
 * source packet. This exception is never raised when publishing [QoS.AT_MOST_ONCE] packets. The failed packets will
 * be retransmitted upon reconnection to the server.
 */
public class HandshakeFailedException(message: String, public val source: Publish) :
    MqttException(message)