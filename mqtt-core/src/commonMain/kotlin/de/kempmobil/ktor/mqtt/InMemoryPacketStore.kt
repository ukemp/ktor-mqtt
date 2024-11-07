package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Pubrel
import de.kempmobil.ktor.mqtt.util.Logger
import kotlinx.datetime.Clock

public class InMemoryPacketStore(private val clock: Clock = Clock.System) : PacketStore {

    private val packets = mutableMapOf<UShort, PacketHolder>()

    override fun store(publish: Publish) {
        require(publish.packetIdentifier != null) { "Packets without packet identifier cannot be part of a transaction" }
        Logger.v { "Storing PUBLISH packet $publish" }
        packets[publish.packetIdentifier] = holderFor(publish)
    }

    override fun replace(
        publish: Publish,
        reason: ReasonCode,
        reasonString: String?
    ): Pubrel {
        val packetIdentifier = publish.packetIdentifier
        require(packetIdentifier != null) { "Packets without packet identifier cannot be part of a transaction" }

        return Pubrel.from(publish)
            .also { pubrel ->
                Logger.v { "Replacing PUBLISH packet with identifier $packetIdentifier with $pubrel" }
                packets[packetIdentifier] = holderFor(pubrel)
            }
    }

    override fun acknowledge(publish: Publish) {
        if (publish.packetIdentifier != null) {
            packets.remove(publish.packetIdentifier)
            Logger.v { "Acknowledged PUBLISH packet $publish" }
        }
    }

    override fun acknowledge(pubrel: Pubrel) {
        packets.remove(pubrel.packetIdentifier)
        Logger.v { "Acknowledged PUBLISH packet $pubrel" }
    }

    override fun unacknowledgedPackets(): List<Packet> {
        return packets.values.sorted().map { it.packet }
    }

    private fun holderFor(packet: Packet) = PacketHolder(packet, clock.now().toEpochMilliseconds())

    private class PacketHolder(val packet: Packet, private val timestamp: Long) : Comparable<PacketHolder> {

        override fun compareTo(other: PacketHolder): Int {
            return this.timestamp.compareTo(other.timestamp)
        }
    }
}