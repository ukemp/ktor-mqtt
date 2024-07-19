package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Connack

public sealed class ConnectionState {

    public abstract val isConnected: Boolean
}

public data object Disconnected : ConnectionState() {

    override val isConnected: Boolean = false
}

public data class Connected(val connack: Connack) : ConnectionState() {

    override val isConnected: Boolean = true
}
