package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.Pingreq
import de.kempmobil.ktor.mqtt.packet.write
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal class WebSocketMqttEngine(private val config: WebSocketEngineConfig) : MqttEngine {

    override val packetResults: SharedFlow<Result<Packet>>
        get() = TODO("Not yet implemented")

    override val connected: StateFlow<Boolean>
        get() = TODO("Not yet implemented")

    override suspend fun start(): Result<Unit> {
        TODO()
    }

    override suspend fun send(packet: Packet): Result<Unit> {
        val client = HttpClient(CIO) {
            install(WebSockets) {
                pingIntervalMillis = 20_000
            }
        }
        client.webSocket {
            val bytes = ByteChannel().apply {
                write(Pingreq)
                flush()
            }
            this.send(Frame.Binary(fin = true, bytes.readBuffer()))
            send(Pingreq)
        }
        TODO()
    }

    override suspend fun disconnect() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}