package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
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
    private val _packetResults = MutableSharedFlow<Result<Packet>>()
    internal val packetResults: SharedFlow<Result<Packet>>
        get() = _packetResults

    private val _connected = MutableStateFlow(false)
    internal val connected: StateFlow<Boolean>
        get() = _connected

    private val selectorManager = SelectorManager(config.dispatcher)

    private val scope = CoroutineScope(config.dispatcher)

    private var sendChannel: ByteWriteChannel? = null

    private var socket: Socket? = null

    private var receiverJob: Job? = null

    internal suspend fun start(): Result<Unit> {
        return try {
            socket = scope.async {
                val socket = openSocket()
                _connected.emit(true)
                socket
            }.await().also { socket ->
                sendChannel = socket.openWriteChannel()

                // It's important to open the read channel here, if we do it in the job below exceptions will be ignored
                val readChannel = socket.openReadChannel()
                receiverJob = scope.launch {
                    readChannel.incomingMessageLoop()
                }
            }
            Result.success(Unit)
        } catch (ex: Exception) {
            Result.failure(ConnectionException("Cannot connect to ${config.host}:${config.port}", ex))
        }
    }

    internal suspend fun send(packet: Packet): Result<Unit> {
        return sendChannel?.doSend(packet)
            ?: Result.failure(ConnectionException("Not connected to ${config.host}:${config.port}"))
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
        while (receiverJob?.isActive == true) {
            try {
                _packetResults.emit(Result.success(readPacket()))
            } catch (ex: CancellationException) {
                Logger.d { "Packet reader job has been cancelled" }
                break
            } catch (ex: ClosedReceiveChannelException) {
                Logger.w(throwable = ex) { "Read channel has been closed, terminating..." }
                break
            } catch (ex: MalformedPacketException) {
                // Continue with the loop, so that the client can decide what to do
                _packetResults.emit(Result.failure(ex))
            } catch (ex: Exception) {
                // On JVM sometimes a java.net.SocketException is thrown instead of ClosedReceiveChannelException
                Logger.w(throwable = ex) { "Read channel error detected, terminating..." }
                break
            }
        }

        Logger.d { "Incoming message loop terminated" }
        disconnected()
    }

    private suspend fun ByteWriteChannel.doSend(packet: Packet): Result<Unit> {
        Logger.v { "Sending $packet..." }

        return try {
            write(packet)
            flush()
            Result.success(Unit)
        } catch (ex: CancellationException) {
            Logger.d { "Packet writer job has been cancelled during write operation" }
            disconnected()
            Result.failure(ex)
        } catch (ex: ClosedWriteChannelException) {
            Logger.w(throwable = ex) { "Write channel has been closed" }
            disconnected()
            Result.failure(ex)
        } catch (ex: Exception) {
            Logger.w(throwable = ex) { "Write channel error detected" }
            disconnected()
            Result.failure(ex)
        }
    }

    private suspend fun disconnected() {
        _connected.emit(false)
        receiverJob?.cancel()
        socket?.close()
        sendChannel?.close()

        receiverJob = null
        socket = null
        sendChannel = null
    }
}