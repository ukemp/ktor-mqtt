package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Pubrel

public interface PacketStore {

    public fun store(publish: Publish)

    public fun replace(publish: Publish, reason: ReasonCode = Success, reasonString: String? = null): Pubrel

    public fun acknowledge(publish: Publish)

    public fun acknowledge(pubrel: Pubrel)

    /**
     * Returns the list of all unacknowledged packet of this packet store. The list must be sorted in the same order as
     * the packets were added to this.
     */
    public fun unacknowledgedPackets(): List<Packet>
}