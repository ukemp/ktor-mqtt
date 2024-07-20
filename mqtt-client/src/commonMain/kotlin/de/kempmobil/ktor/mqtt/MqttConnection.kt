package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal interface MqttConnection {

    val packetResults: SharedFlow<Result<Packet>>

    val connected: StateFlow<Boolean>

    suspend fun start(): Result<Unit>

    suspend fun send(packet: Packet): Result<Unit>

    suspend fun disconnect()

    fun close()
}