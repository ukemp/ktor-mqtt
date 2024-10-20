package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for doing the network stuff of sending and receiving MQTT packets.
 */
public interface MqttEngine {

    public val packetResults: SharedFlow<Result<Packet>>

    public val connected: StateFlow<Boolean>

    public suspend fun start(): Result<Unit>

    public suspend fun send(packet: Packet): Result<Unit>

    public suspend fun disconnect()

    public fun close()
}