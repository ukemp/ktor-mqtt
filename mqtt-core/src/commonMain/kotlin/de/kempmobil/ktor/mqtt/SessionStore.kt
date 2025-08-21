package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Pubrel

/**
 * Stores the session state for QoS 1 and QoS 2 packets.
 *
 * The session state primarily consists of:
 * 1. Outgoing QoS 1 and QoS 2 messages that have not been fully acknowledged by the server.
 * 2. Packet identifiers of incoming QoS 2 PUBLISH packets that have been received but for which the
 *    QoS 2 exchange has not yet been completed.
 */
public interface SessionStore {

    // ---- Outgoing Message Flow (Client -> Server) ----

    /**
     * Stores an outgoing PUBLISH packet that requires an acknowledgement (QoS 1 or QoS 2).
     *
     * This method must be thread safe.
     *
     * @param packet The PUBLISH packet to store. Must have a non-null packet identifier.
     * @return an in-flight packet with the current time as timestamp and the original packet.
     * @throws IllegalArgumentException if the packet identifier is null.
     */
    public fun store(source: Publish): InFlightPublish

    /**
     * Replaces a stored outgoing in-flight PUBLISH packet with an in-flight PUBREL packet. This is part of the QoS 2
     * flow, occurring after a PUBREC is received from the server.
     *
     * This method must be thread safe.
     *
     * @param source The original in-flight PUBLISH packet that is being replaced.
     * @return The created PUBREL in-flight packet
     * @throws NoSuchElementException if no corresponding PUBLISH packet is found in the store.
     */
    public fun replace(source: InFlightPublish): InFlightPubrel

    /**
     * Removes an outgoing in-flight PUBLISH or PUBREL packet from the store.
     *
     * This method must be thread safe.
     *
     * @param packet The packet that has been acknowledged.
     */
    public fun acknowledge(packet: InFlightPacket)

    /**
     * Returns the list of all unacknowledged packet of this packet store (PUBLISH and PUBREL). The list must be sorted
     * in the same order as the packets were added to this, and it must not include PUBLISH packets which are expired
     * due to their message expiry interval.
     */
    public fun unacknowledgedPackets(): List<InFlightPacket>

    // ---- Incoming Message Flow (Server -> Client) ----

    /**
     * Stores the packet identifier of an incoming QoS 2 PUBLISH packet. This is to prevent reprocessing of the same
     * message if the server re-delivers it. The client responds with a PUBREC and stores the identifier until a PUBREL
     * is received.
     *
     * @param publish The received QoS 2 PUBLISH packet.
     * @return `true` if the identifier was not already present, `false` otherwise (indicating a re-delivery).
     */
    public fun rememberIncomingPacketId(publish: Publish): Boolean

    /**
     * Checks if the packet identifier from an incoming QoS 2 PUBLISH is already stored.
     *
     * @param publish The PUBLISH packet that contains the packet identifier to check.
     * @return `true` if the identifier is already stored, `false` otherwise.
     */
    public fun hasIncomingPacketId(publish: Publish): Boolean

    /**
     * Removes the packet identifier of an incoming QoS 2 PUBLISH packet from the store. This is called after the client
     * has received the corresponding PUBREL from the server, completing the QoS 2 exchange for that message.
     *
     * @param pubrel the PUBREL packet that has been acknowledged.
     */
    public fun releaseIncomingPacketId(pubrel: Pubrel)

    /**
     * Clears all persisted session state. This should be called when the client connects with `cleanStart = true`.
     */
    public fun clear()
}