package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Publish
import de.kempmobil.ktor.mqtt.packet.Pubrel
import de.kempmobil.ktor.mqtt.util.toTopic
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class InMemorySessionStoreTest {

    private lateinit var store: SessionStore

    private val clock = Clock.System

    @BeforeTest
    fun setup() {
        store = InMemorySessionStore()
    }

    @Test
    fun `cannot store PUBLISH packets without session identifier`() {
        val publish = Publish(topic = "topic".toTopic(), payload = ByteString("payload".toByteArray()))

        assertFailsWith<IllegalArgumentException> { store.store(publish) }
    }

    @Test
    fun `throw NoSuchElementException when replacing unknown PUBLISH packet`() {
        val inFlight = InFlightPublish(publishPacket(), clock.now(), 42)
        assertFailsWith<NoSuchElementException> { store.replace(inFlight) }
    }

    @Test
    fun `replace an existing PUBLISH packet with a PUBREL packet`() {
        val inFlight = store.store(publishPacket(packetIdentifier = 42u))
        val pubrel = store.replace(inFlight)

        assertEquals(42u, pubrel.packetIdentifier)
    }

    @Test
    fun `acknowledged PUBLISH packets are removed from the store`() {
        val publish = publishPacket()

        val inFlight = store.store(publish)
        store.acknowledge(inFlight)

        assertEquals(emptyList(), store.unacknowledgedPackets())
    }

    @Test
    fun `acknowledged PUBREL packets are removed from the store`() {
        val publish = publishPacket()

        val inFlight = store.store(publish)
        val pubrel = store.replace(inFlight)
        store.acknowledge(pubrel)

        assertEquals(emptyList(), store.unacknowledgedPackets())
    }

    @Test
    fun `unacknowledged packets are returned in the order they were added`() {
        val identifiers = listOf<UShort>(4u, 5u, 7u, 99u, 1u, 2u, 150u, 148u, 2000u, 2001u, 1999u)

        identifiers.forEach {
            store.store(publishPacket(it))
        }

        val unacknowledged: List<UShort> = store.unacknowledgedPackets().map { it.packetIdentifier }
        assertEquals(identifiers, unacknowledged)
    }

    // ---- Incoming messages

    @Test
    fun `cannot store incoming PUBLISH packet without packet identifier`() {
        val publish = Publish(topic = "topic".toTopic(), payload = ByteString("payload".toByteArray()))

        assertFailsWith<IllegalArgumentException> { store.rememberIncomingPacketId(publish) }
    }

    @Test
    fun `storing incoming PUBLISH packets`() {
        val publish = publishPacket(packetIdentifier = 42u)

        assertFalse(store.rememberIncomingPacketId(publish))
        assertTrue(store.rememberIncomingPacketId(publish))
        assertTrue(store.hasIncomingPacketId(publish))
    }

    @Test
    fun `unknown incoming packets are identified as such`() {
        val unknownPacketIdentifier = publishPacket(packetIdentifier = 42u)
        assertFalse(store.hasIncomingPacketId(unknownPacketIdentifier))

        val missingPacketIdentifier = Publish(topic = "topic".toTopic(), payload = ByteString("payload".toByteArray()))
        assertFalse(store.hasIncomingPacketId(missingPacketIdentifier))
    }

    @Test
    fun `release incoming PUBLISH packets`() {
        val publish = publishPacket(packetIdentifier = 42u)

        store.rememberIncomingPacketId(publish)
        assertTrue(store.hasIncomingPacketId(publish))

        store.releaseIncomingPacketId(Pubrel.from(publish))
        assertFalse(store.hasIncomingPacketId(publish))
    }

    @Test
    fun `clear the session store`() {
        val incoming = publishPacket(packetIdentifier = 3u)
        store.store(publishPacket(packetIdentifier = 2u))
        store.rememberIncomingPacketId(incoming)
        store.clear()

        assertEquals(emptyList(), store.unacknowledgedPackets())
        assertFalse(store.hasIncomingPacketId(incoming))
    }

    private fun publishPacket(packetIdentifier: UShort = 10u) = Publish(
        qoS = QoS.AT_LEAST_ONCE,
        packetIdentifier = packetIdentifier,
        topic = "topic".toTopic(),
        payload = ByteString("payload".toByteArray())
    )
}