package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.PacketReceiver
import de.kempmobil.ktor.mqtt.packet.readPacket
import de.kempmobil.ktor.mqtt.packet.write
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal class MqttConnection(
    private val config: MqttClientConfig,
    private val receiver: PacketReceiver
) {
    private val selectorManager = SelectorManager(config.dispatcher)

    private val scope = CoroutineScope(config.dispatcher)

    private val outPackets = MutableSharedFlow<Packet>(replay = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var socket: Socket? = null

    private var connectJob: Job? = null

    internal fun start() {
        connectJob = scope.launch {
            socket = openSocket().also { socket ->
                val receiveChannel = socket.openReadChannel()
                val sendChannel = socket.openWriteChannel()
                launch { receiveChannel.incomingMessageLoop() }
                launch { sendChannel.outgoingMessagesLoop() }
            }
        }
    }

    internal fun stop() {
        connectJob?.cancel()
        socket?.close()
        selectorManager.close()
    }

    internal fun send(packet: Packet) {
        scope.launch {
            outPackets.emit(packet)
        }
    }

    // --- Private methods ---------------------------------------------------------------------------------------------

    private suspend fun openSocket(): Socket {
        val socket = aSocket(selectorManager).tcp().connect(config.host, config.port)
        config.tlsConfig?.let { tlsConfigBuilder ->
            socket.tls(config.dispatcher) {
                takeFrom(tlsConfigBuilder)
            }
        }
        return socket
    }

    private suspend fun ByteReadChannel.incomingMessageLoop() {
        try {
            while (connectJob?.isActive == true) {
                readPacket(receiver)
            }
        } catch (ex: CancellationException) {
            Logger.d { "Packet reader job has been cancelled" }
            return
        } catch (ex: Exception) {
            Logger.d(throwable = ex) { "Unexpected exception waiting reading bytes" }
            return
        }
        Logger.d { "Packet reader job terminated gracefully" }
    }

    private suspend fun ByteWriteChannel.outgoingMessagesLoop() {
        try {
            outPackets.collect { packet ->
                Logger.d { "Received new ${packet.type.name} packet for sending to ${config.host}:${config.port}" }
                write(packet)
                flush()
            }
        } catch (ex: CancellationException) {
            Logger.d { "Packet writer job has been cancelled" }
        } catch (ex: Exception) {
            Logger.d(throwable = ex) { "Unexpected exception writing bytes" }
            return
        }
    }
}