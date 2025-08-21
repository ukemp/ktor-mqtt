@file:OptIn(ExperimentalTime::class)

package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Pubrel
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents a packet which is currently not completely acknowledged by the server.
 *
 * @property timestamp the time when this packet was created
 * @property key an integer value used for sorting instances of this
 */
public sealed class InFlightPacket(
    public val timestamp: Instant,
    public val key: Long
) : Comparable<InFlightPacket> {

    /**
     * The packet identifier of the underlying packet.
     */
    public abstract val packetIdentifier: UShort

    /**
     * Determines whether this in-flight packet is expired due to its message expiry interval
     */
    public abstract fun isExpired(now: Instant): Boolean

    override fun compareTo(other: InFlightPacket): Int {
        return this.key.compareTo(other.key)
    }
}

public class InFlightPublish(public val source: Publish, timestamp: Instant, id: Long) : InFlightPacket(timestamp, id) {

    init {
        require(source.qoS != QoS.AT_MOST_ONCE) {
            "PUBLISH packets with QoS 0 cannot be part of a transaction: $source"
        }
    }

    override val packetIdentifier: UShort
        get() = source.packetIdentifier!!

    override fun isExpired(now: Instant): Boolean {
        return source.messageExpiryInterval != null && timestamp + source.messageExpiryInterval.toDuration() < now
    }
}

public class InFlightPubrel(public val source: Pubrel, timestamp: Instant, id: Long) : InFlightPacket(timestamp, id) {

    public constructor(inFlightPublish: InFlightPublish, id: Long) :
            this(Pubrel.from(inFlightPublish.source), inFlightPublish.timestamp, id)

    override val packetIdentifier: UShort
        get() = source.packetIdentifier

    override fun isExpired(now: Instant): Boolean {
        return false
    }
}

