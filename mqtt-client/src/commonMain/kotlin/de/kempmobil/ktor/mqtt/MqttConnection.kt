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

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    internal val connectionState: StateFlow<ConnectionState>
        get() = _connectionState

    private val selectorManager = SelectorManager(config.dispatcher)

    private val scope = CoroutineScope(config.dispatcher)

    private var sendChannel: ByteWriteChannel? = null

    private var socket: Socket? = null

    private var receiverJob: Job? = null

    internal suspend fun start() {
        try {
            socket = scope.async {
                _connectionState.emit(ConnectionState.CONNECTING)
                val socket = openSocket()
                _connectionState.emit(ConnectionState.CONNECTED)
                socket
            }.await().also { socket ->
                sendChannel = socket.openWriteChannel()
                receiverJob = scope.launch {
                    socket.openReadChannel().incomingMessageLoop()
                }
            }
        } catch (ex: Exception) {
            _connectionState.emit(ConnectionState.DISCONNECTED)
            throw ConnectionException("Cannot connect to ${config.host}:${config.port}", ex)
        }
    }

    internal suspend fun send(packet: Packet): Boolean {
        sendChannel?.run {
            Logger.v { "Sending $packet..." }
            write(packet)
            flush()

            if (packet is Disconnect) {
                Logger.i { "Disconnect message sent to server, terminating the connection now" }
                this@MqttConnection.close()
            }
            return true
        } ?: return false
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
            _connectionState.emit(ConnectionState.DISCONNECTED)
            return
        } catch (ex: ClosedReceiveChannelException) {
            Logger.d { "Read channel has been closed, terminating..." }
            _connectionState.emit(ConnectionState.DISCONNECTED)
            return
        } catch (ex: MalformedPacketException) {
            Logger.e(throwable = ex) { "Malformed packet received, sending DISCONNECT" }
            send(Disconnect(reason = MalformedPacket, sessionExpiryInterval = config.sessionExpiryInterval))
            return
        }

        Logger.d { "Packet reader job terminated gracefully" }
        _connectionState.emit(ConnectionState.DISCONNECTED)
    }

    private suspend fun close() {
        _connectionState.emit(ConnectionState.DISCONNECTED)
        receiverJob?.cancel()
        socket?.close()
        sendChannel?.close()
        selectorManager.close()

        receiverJob = null
        socket = null
        sendChannel = null
    }
}