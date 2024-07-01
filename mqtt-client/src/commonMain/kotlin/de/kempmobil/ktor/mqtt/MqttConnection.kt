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
import kotlinx.coroutines.flow.SharedFlow

internal class MqttConnection(
    private val config: MqttClientConfig
) {
    private val selectorManager = SelectorManager(config.dispatcher)

    private val scope = CoroutineScope(config.dispatcher)

    private var sendChannel: ByteWriteChannel? = null

    private var socket: Socket? = null

    private var receiverJob: Job? = null

    private val _packetsReceived = MutableSharedFlow<Packet>()
    internal val packetsReceived: SharedFlow<Packet>
        get() = _packetsReceived

    internal suspend fun start() {
        try {
            socket = scope.async {
                openSocket()
            }.await().also { socket ->
                sendChannel = socket.openWriteChannel()
                receiverJob = scope.launch {
                    socket.openReadChannel().incomingMessageLoop()
                }
            }
        } catch (ex: Exception) {
            throw ConnectionException(ex)
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
        return if (config.tlsConfig == null) {
            aSocket(selectorManager).tcp().connect(config.host, config.port)
        } else {
            aSocket(selectorManager).tcp().connect(config.host, config.port).tls(config.dispatcher) {
                config.tlsConfig.build()
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
            return
        } catch (ex: ClosedReceiveChannelException) {
            Logger.d { "Read channel has been closed, terminating..." }
            return
        } catch (ex: MalformedPacketException) {
            Logger.e(throwable = ex) { "Malformed packet received, sending DISCONNECT" }
            send(Disconnect(reason = MalformedPacket, sessionExpiryInterval = config.sessionExpiryInterval))
            return
        }
        Logger.d { "Packet reader job terminated gracefully" }
    }

    private fun close() {
        receiverJob?.cancel()
        socket?.close()
        sendChannel?.close()
        selectorManager.close()

        receiverJob = null
        socket = null
        sendChannel = null
    }
}