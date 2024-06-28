package de.kempmobil.ktor.mqtt.packet

// TODO: if we don't need class PacketType, we can delete this as well
internal enum class PacketDirection {

    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT,
    BOTH;
}