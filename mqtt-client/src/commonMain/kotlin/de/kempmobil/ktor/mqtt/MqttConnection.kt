package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.Disconnect
import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.readPacket
import de.kempmobil.ktor.mqtt.packet.write
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal class MqttConnection(
    private val config: MqttClientConfig
) {
    private val _packetsReceived = MutableSharedFlow<Packet>()
    internal val packetsReceived: SharedFlow<Packet>
        get() = _packetsReceived

    private val _state = MutableStateFlow<ConnectionState>(Disconnected)
    internal val state: StateFlow<ConnectionState>
        get() = _state

    private val selectorManager = SelectorManager(config.dispatcher)

    private val scope = CoroutineScope(config.dispatcher)

    private var sendChannel: ByteWriteChannel? = null

    private var socket: Socket? = null

    private var receiverJob: Job? = null

    internal suspend fun start(): Result<Unit> {
        return runCatching {
            socket = scope.async {
                val socket = openSocket()
                _state.emit(Connected)
                socket
            }.await().also { socket ->
                sendChannel = socket.openWriteChannel()

                // It's important to open the read channel here, if we do it in the job below exceptions will be ignored
                val readChannel = socket.openReadChannel()
                receiverJob = scope.launch {
                    readChannel.incomingMessageLoop()
                }
            }
        }
    }

    internal suspend fun send(packet: Packet): Boolean {
        sendChannel?.run {
            Logger.v { "Sending $packet..." }
            write(packet)
            flush()

            if (packet is Disconnect) {
                Logger.i { "Disconnect message sent to server, terminating the connection now" }
                this@MqttConnection.disconnected()
            }
            return true
        } ?: return false
    }

    internal suspend fun disconnect() {
        socket?.let {
            socket = null
            it.close()
        }
        disconnected()
    }

    internal fun close() {
        selectorManager.close()
    }

    // --- Private methods ---------------------------------------------------------------------------------------------

    private suspend fun openSocket(): Socket {
        return with(config) {
            if (tlsConfig == null) {
                aSocket(selectorManager).tcp().connect(host, port, tcpOptions)
            } else {
                aSocket(selectorManager).tcp().connect(host, port, tcpOptions).tls(dispatcher) {
                    tlsConfig.build()
                }
            }
        }
    }

    private suspend fun ByteReadChannel.incomingMessageLoop() {
        try {
            while (receiverJob?.isActive == true) {
                val packet = readPacket()
                if (packet is Disconnect) {
                    Logger.i { "Received $packet from ${config.host}" }
                }
                _packetsReceived.emit(packet)
            }
        } catch (ex: CancellationException) {
            Logger.d { "Packet reader job has been cancelled" }
            _state.emit(Disconnected)
            return
        } catch (ex: ClosedReceiveChannelException) {
            Logger.d { "Read channel has been closed, terminating..." }
            _state.emit(Disconnected)
            return
        } catch (ex: MalformedPacketException) {
            Logger.e(throwable = ex) { "Malformed packet received, sending DISCONNECT" }
            send(Disconnect(reason = MalformedPacket, sessionExpiryInterval = config.sessionExpiryInterval))
            return
        }

        Logger.d { "Packet reader job terminated gracefully" }
        _state.emit(Disconnected)
    }

    private suspend fun disconnected() {
        _state.emit(Disconnected)
        receiverJob?.cancel()
        socket?.close()
        sendChannel?.close()

        receiverJob = null
        socket = null
        sendChannel = null
    }
}