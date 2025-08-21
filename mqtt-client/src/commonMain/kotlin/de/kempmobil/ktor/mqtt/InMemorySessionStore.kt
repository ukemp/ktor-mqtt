@file:OptIn(ExperimentalAtomicApi::class, ExperimentalTime::class)

package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Pubrel
import de.kempmobil.ktor.mqtt.util.Logger
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

public open class InMemorySessionStore(private val clock: Clock = Clock.System) : SessionStore {

    private val outgoingPackets = AtomicMap<UShort, InFlightPacket>()

    private val incomingPackets = mutableSetOf<UShort>()

    private val sequence = AtomicLong(0)

    override fun store(source: Publish): InFlightPublish {
        Logger.v { "Storing in-flight packet $source" }
        val packet = InFlightPublish(source, clock.now(), sequence.incrementAndFetch())
        outgoingPackets[packet.packetIdentifier] = packet
        return packet
    }

    override fun replace(source: InFlightPublish): InFlightPubrel {
        val packetIdentifier = source.packetIdentifier

        if (!outgoingPackets.containsKey(packetIdentifier)) {
            throw NoSuchElementException("No PUBLISH packet found with identifier $packetIdentifier")
        }

        return InFlightPubrel(source, sequence.incrementAndFetch())
            .also { inFlight ->
                Logger.v { "Replacing PUBLISH packet with identifier $packetIdentifier with $inFlight" }
                outgoingPackets[packetIdentifier] = inFlight
            }
    }

    override fun acknowledge(packet: InFlightPacket) {
        outgoingPackets.remove(packet.packetIdentifier)
        Logger.v { "Acknowledged PUBLISH packet $packet" }
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

    override fun unacknowledgedPackets(): List<InFlightPacket> {
        // Does not need to be thread safe:
        val now = clock.now()
        val all = outgoingPackets.ref.load().filterNot { it.value.isExpired(now) }
        outgoingPackets.ref.store(all)

        return all.values.sorted()
    }

    override fun clear() {
        outgoingPackets.clear()
        incomingPackets.clear()
    }
}

private class AtomicMap<K, V>() {

    val ref = AtomicReference(emptyMap<K, V>())

    fun containsKey(key: K): Boolean {
        return ref.load().containsKey(key)
    }

    operator fun set(key: K, value: V) {
        while (true) {
            val current = ref.load()
            val updated = current + (key to value)
            if (ref.compareAndSet(current, updated)) {
                break
            }
        }
    }

    fun remove(key: K) {
        while (true) {
            val current = ref.load()
            if (!current.containsKey(key)) {
                break // Nothing to remove
            }
            val updated = current - key
            if (ref.compareAndSet(current, updated)) {
                break
            }
        }
    }

    fun clear() {
        ref.store(emptyMap())
    }
}