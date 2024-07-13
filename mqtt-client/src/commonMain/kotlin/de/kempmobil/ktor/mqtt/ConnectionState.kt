package de.kempmobil.ktor.mqtt

internal sealed class ConnectionState

internal data object Disconnected : ConnectionState()

internal data object Connected : ConnectionState()
