package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.Suback
import de.kempmobil.ktor.mqtt.packet.Subscribe

internal fun Subscribe.isAssociatedSuback(packet: Packet): Boolean {
    return packet is Suback && packetIdentifier == packet.packetIdentifier
}