package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.readPacket
import de.kempmobil.ktor.mqtt.packet.write
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.io.Buffer

internal class WebSocketMqttEngine(private val config: WebSocketEngineConfig) : MqttEngine {

    private val client: HttpClient = config.clientFactory()

    private val _packetResults = MutableSharedFlow<Result<Packet>>()
    override val packetResults: SharedFlow<Result<Packet>>
        get() = _packetResults

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean>
        get() = _connected

    private val scope = CoroutineScope(config.dispatcher)

    private var receiverJob: Job? = null
    private var wsSession: DefaultClientWebSocketSession? = null

    init {
        if (client.pluginOrNull(WebSockets) == null) {
            throw IllegalStateException("No WebSockets plugin installed in ${client.engine::class.simpleName}, consider using 'install(WebSockets)'")
        }
    }

    override suspend fun start(): Result<Unit> {
        return try {
            wsSession = client.webSocketSession(
                method = HttpMethod.Get,
                host = config.host,
                port = config.port,
                path = config.path
            ) {
                url.protocol = if (config.useWss) URLProtocol.WSS else URLProtocol.WS
                headers[HttpHeaders.SecWebSocketProtocol] = "mqtt"
            }.also {
                _connected.emit(true)
                receiverJob = scope.launch {
                    it.incomingMessagesLoop()
                }
            }
            Result.success(Unit)
        } catch (ex: Exception) {
            Result.failure(ConnectionException("Cannot connect to ${config.host}:${config.port}", ex))
        }
    }

    override suspend fun send(packet: Packet): Result<Unit> {
        return wsSession?.doSend(packet)
            ?: Result.failure(ConnectionException("Not connected to ${config.host}:${config.port}"))
    }

    override suspend fun disconnect() {
        wsSession?.close()
        receiverJob?.cancel()
        _connected.emit(false)
    }

    override fun close() {
        client.close()
    }

    override fun toString(): String {
        return "WebSocketMqttEngine[${config.host}:${config.port}]"
    }

    private suspend fun DefaultClientWebSocketSession.incomingMessagesLoop() {
        while (receiverJob?.isActive == true) {
            try {
                Logger.d { "${this@WebSocketMqttEngine} waiting for incoming frames..." }

                for (frame in incoming) {
                    when (frame) {
                        // Note that in non-raw mode, we should never receive Close, Ping or Pong frames
                        is Frame.Binary -> {
                            Logger.d { "${this@WebSocketMqttEngine} received data frame of size: ${frame.data.size}" }
                            with(Buffer()) {
                                writeFully(frame.readBytes())
                                _packetResults.emit(Result.success(readPacket()))
                            }
                        }

                        else -> {
                            Logger.e { "${this@WebSocketMqttEngine} received unexpected frame: $frame" }
                        }
                    }
                }
                Logger.d { "${this@WebSocketMqttEngine} frames terminated, cancelling incoming message queue" }

                // When we come here, the connection has been terminated, hence do some cleanup
                disconnect()

            } catch (ex: CancellationException) {
                Logger.d { "Incoming message queue of ${this@WebSocketMqttEngine} has been cancelled" }
                disconnect()
            } catch (ex: MalformedPacketException) {
                // Continue with the loop, so that the client can decide what to do
                _packetResults.emit(Result.failure(ex))
            } catch (ex: Exception) {
                Logger.e(throwable = ex) { "${this@WebSocketMqttEngine} error while receiving messages: " + ex::class }
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.doSend(packet: Packet): Result<Unit> {
        Logger.v { "Sending $packet..." }

        return try {
            with(Buffer()) {
                write(packet)
                outgoing.send(Frame.Binary(fin = true, packet = this))
            }
            Result.success(Unit)
        } catch (ex: Exception) {
            Logger.w(throwable = ex) { "Write socket error detected" }
            Result.failure(ex)
        }
    }
}