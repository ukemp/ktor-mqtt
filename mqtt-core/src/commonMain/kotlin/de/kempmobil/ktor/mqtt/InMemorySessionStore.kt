package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Pubrel
import de.kempmobil.ktor.mqtt.util.Logger
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
public class InMemorySessionStore() : SessionStore {

    private val outgoingPackets = mutableMapOf<UShort, PacketHolder>()

    private val incomingPackets = mutableSetOf<UShort>()

    private val sequence = AtomicLong(0)

    override fun store(publish: Publish) {
        require(publish.packetIdentifier != null) { "Packets without packet identifier cannot be part of a transaction" }
        Logger.v { "Storing PUBLISH packet $publish" }
        outgoingPackets[publish.packetIdentifier] = holderFor(publish)
    }

    override fun replace(publish: Publish): Pubrel {
        val packetIdentifier = publish.packetIdentifier
        require(packetIdentifier != null) { "Packets without packet identifier cannot be part of a transaction" }

        if (outgoingPackets[packetIdentifier] == null) {
            throw NoSuchElementException("No PUBLISH packet found with identifier $packetIdentifier")
        }

        return Pubrel.from(publish)
            .also { pubrel ->
                Logger.v { "Replacing PUBLISH packet with identifier $packetIdentifier with $pubrel" }
                outgoingPackets[packetIdentifier] = holderFor(pubrel)
            }
    }

    override fun acknowledge(publish: Publish) {
        if (publish.packetIdentifier != null) {
            outgoingPackets.remove(publish.packetIdentifier)
            Logger.v { "Acknowledged PUBLISH packet $publish" }
        }
    }

    override fun acknowledge(pubrel: Pubrel) {
        outgoingPackets.remove(pubrel.packetIdentifier)
        Logger.v { "Acknowledged PUBLISH packet $pubrel" }
    }

    override fun rememberIncomingPacketId(publish: Publish): Boolean {
        val packetIdentifier = publish.packetIdentifier
        require(packetIdentifier != null) { "Packets without packet identifier cannot be part of a transaction" }

        return !incomingPackets.add(packetIdentifier)
    }

    override fun hasIncomingPacketId(publish: Publish): Boolean {
        return incomingPackets.contains(publish.packetIdentifier)
    }

    override fun releaseIncomingPacketId(pubrel: Pubrel) {
        incomingPackets.remove(pubrel.packetIdentifier)
    }

    override fun unacknowledgedPackets(): List<Packet> {
        return outgoingPackets.values.sorted().map { it.packet }
    }

    override fun clear() {
        outgoingPackets.clear()
        incomingPackets.clear()
    }

    private fun holderFor(packet: Packet) = PacketHolder(packet, sequence.incrementAndFetch())

    private class PacketHolder(val packet: Packet, private val id: Long) : Comparable<PacketHolder> {

        override fun compareTo(other: PacketHolder): Int {
            return this.id.compareTo(other.id)
        }
    }
}