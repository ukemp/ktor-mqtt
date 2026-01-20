package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Publish

public open class MqttException internal constructor(message: String? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Indicates that a received packet could not be parsed.
 */
public class MalformedPacketException(message: String? = null) :
    MqttException(message)

/**
 * Indicates that a protocol error was detected. For example the server sent a "receive maximum" value of 0.
 */
public class ProtocolErrorException(message: String? = null) :
    MqttException(message)

/**
 * Indicates that a server connection cannot be established or was disconnected.
 */
public class ConnectionException(message: String? = null, cause: Throwable? = null) :
    MqttException(message = message, cause = cause)

/**
 * Indicates that a packet was not received within the expected time.
 */
public class TimeoutException(message: String) :
    MqttException(message)

/**
 * Indicates that the topic alias value of a publish request exceed the "Topic Alias Maximum" sent by the server.
 */
public class TopicAliasException(message: String?) :
    MqttException(message)

/**
 * Indicates that the handshake procedure for [QoS.AT_LEAST_ONCE] or for [QoS.EXACTLY_ONE] has failed for the specified
 * source packet. This exception is never raised when publishing [QoS.AT_MOST_ONCE] packets. The failed packets will
 * be retransmitted upon reconnection to the server.
 */
public class HandshakeFailedException(message: String, public val source: Publish) :
    MqttException(message)