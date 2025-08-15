package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Puback
import de.kempmobil.ktor.mqtt.packet.Pubcomp
import de.kempmobil.ktor.mqtt.packet.Publish

/**
 * Return value of a successful PUBLISH request.
 *
 * @see MqttClient.publish
 * @see AtMostOncePublishResponse
 * @see AtLeastOncePublishResponse
 * @see ExactlyOnePublishResponse
 */
public sealed class PublishResponse() {

    /**
     * The packet which was successfully delivered to the server.
     */
    public abstract val source: Publish

    /**
     * The reason code of the final packet that was returned by the server. Its source depends on the quality of service
     * which was actually used for publishing:
     *
     * - for AT_MOST_ONCE this will always be [Success]
     * - for AT_LEAST_ONCE this will be taken from the [Puback] packet
     * - for EXACTLY_ONE this will be taken from the [Pubcomp] packet
     *
     * Note that even for a successful delivery to the server, the reason code might not always be zero (success). For
     * example, when publishing with [QoS.AT_LEAST_ONCE] to a topic without subscribers, the returned reason might be
     * [NoMatchingSubscribers].
     */
    public abstract val reason: ReasonCode
}

/**
 * Returns the quality of service that was used for sending the PUBLISH request.
 */
public val PublishResponse.qoS: QoS
    get() = source.qoS

/**
 * The publish response for [QoS.AT_MOST_ONCE].
 */
public data class AtMostOncePublishResponse(public override val source: Publish) : PublishResponse() {

    override val reason: ReasonCode = Success
}

/**
 * The publish response for [QoS.AT_LEAST_ONCE].
 */
public data class AtLeastOncePublishResponse(
    public override val source: Publish,
    public val puback: Puback
) : PublishResponse() {

    override val reason: ReasonCode = puback.reason
}

/**
 * The publish response for [QoS.EXACTLY_ONE].
 */
public data class ExactlyOnePublishResponse(
    public override val source: Publish,
    public val pubcomp: Pubcomp
) : PublishResponse() {

    override val reason: ReasonCode = pubcomp.reason
}
