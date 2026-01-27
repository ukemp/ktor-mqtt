package de.kempmobil.ktor.mqtt.ws

import de.kempmobil.ktor.mqtt.ConnectionException
import de.kempmobil.ktor.mqtt.MalformedPacketException
import de.kempmobil.ktor.mqtt.MqttEngine
import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.readPacket
import de.kempmobil.ktor.mqtt.packet.write
import de.kempmobil.ktor.mqtt.util.Logger
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.Buffer

/**
 * @property config the engine config
 * @param replay the size of the replay cache for [packetResults], mainly used for testing
 */
internal class WebSocketEngine(private val config: WebSocketEngineConfig, replay: Int = 0) : MqttEngine {

    private val client: HttpClient = config.http()

    private val _packetResults = MutableSharedFlow<Result<Packet>>(replay = replay)
    override val packetResults = _packetResults.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    override val connected = _connected.asStateFlow()

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
            if (!config.url.user.isNullOrBlank() || !config.url.password.isNullOrBlank()) {
                Logger.w { "Username/password encoded in URL cannot be used in websocket connections" }
            }
            wsSession = client.webSocketSession(
                method = HttpMethod.Get,
                host = config.url.host,
                port = config.url.port,
                path = config.url.encodedPath
            ) {
                url.protocol = when (val protocol = config.url.protocol) {
                    URLProtocol.WS, URLProtocol.HTTP -> URLProtocol.WS
                    URLProtocol.WSS, URLProtocol.HTTPS -> URLProtocol.WSS
                    else -> {
                        throw IllegalArgumentException("Unexpected web socket protocol: $protocol (use http(s) or ws(s))")
                    }
                }
                headers[HttpHeaders.SecWebSocketProtocol] = "mqtt"
            }.also {
                _connected.value = true
                receiverJob = scope.launch {
                    it.incomingMessagesLoop()
                }
            }
            Result.success(Unit)
        } catch (ex: Exception) {
            Result.failure(ConnectionException("Cannot connect to ${config.url}", ex))
        }
    }

    override suspend fun send(packet: Packet): Result<Unit> {
        return wsSession?.doSend(packet)
            ?: Result.failure(ConnectionException("Not connected to ${config.url}"))
    }

    override suspend fun disconnect() {
        _connected.value = false
        wsSession?.close()
        receiverJob?.cancel()
    }

    override fun close() {
        client.close()
    }

    override fun toString(): String {
        return "WebSocketMqttEngine[${config.url}]"
    }

    private suspend fun DefaultClientWebSocketSession.incomingMessagesLoop() {
        // As we cannot assume that MQTT Control Packets are aligned on WebSocket frame boundaries. Hence, use a
        // ByteChannel where we write all received binary frames to and read the packets from it, as soon as a complete
        // packet is available. This works because unlike Buffer.read(), Channel.read() can suspend until new bytes are
        // available.
        val channel = ByteChannel(autoFlush = true)
        val reader = launch {
            while (!channel.isClosedForRead) {
                val result = try {
                    Result.success(channel.readPacket())
                } catch (ex: MalformedPacketException) {
                    Result.failure(ex)
                } catch (_: CancellationException) {
                    break
                }
                _packetResults.emit(result)
            }
        }

        try {
            for (frame in incoming) {
                when (frame) {
                    // Note that in non-raw mode, we should never receive Close, Ping or Pong frames
                    is Frame.Binary -> {
                        Logger.v { "Received data frame of size: ${frame.data.size}" }
                        channel.writeFully(frame.readBytes())
                    }

                    else -> {
                        // TODO: Close the network connection when receiving a non-binary frame [MQTT-6.0.0-1]
                        Logger.e { "Received unexpected frame type: $frame" }
                    }
                }
            }
        } catch (_: CancellationException) {
            Logger.v { "Incoming message queue of ${this@WebSocketEngine} has been cancelled" }
        } catch (ex: Exception) {
            Logger.e(throwable = ex) { "Error while receiving messages: " + ex::class }
        } finally {
            Logger.d { "Incoming message loop terminated (no more web socket frames available)" }

            // When we come here, the connection has been terminated, hence do some cleanup
            disconnect()
            reader.cancel()
        }
    }

    private suspend fun DefaultClientWebSocketSession.doSend(packet: Packet): Result<Unit> {
        Logger.d { "Sending $packet..." }

        return try {
            with(Buffer()) {
                write(packet)

                if (size <= maxFrameSize) {
                    outgoing.send(Frame.Binary(fin = true, packet = this))
                } else {
                    val frame = Buffer()
                    while (size > 0) {
                        readAtMostTo(frame, size.coerceAtMost(maxFrameSize))
                        outgoing.send(Frame.Binary(fin = true, packet = frame))
                    }
                }
            }
            Result.success(Unit)
        } catch (ex: Exception) {
            Logger.w(throwable = ex) { "Write socket error detected" }
            Result.failure(ex)
        }
    }
}
