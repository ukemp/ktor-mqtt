package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.packet.Packet
import de.kempmobil.ktor.mqtt.packet.readPacket
import de.kempmobil.ktor.mqtt.packet.write
import de.kempmobil.ktor.mqtt.util.Logger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.EOFException

internal class DefaultEngine(private val config: DefaultEngineConfig) : MqttEngine {

    private val _packetResults = MutableSharedFlow<Result<Packet>>()
    override val packetResults: SharedFlow<Result<Packet>>
        get() = _packetResults

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean>
        get() = _connected

    private val selectorManager = SelectorManager(config.dispatcher)

    private var scope = CoroutineScope(config.dispatcher + SupervisorJob())

    private var sendChannel: ByteWriteChannel? = null

    private var socket: Closeable? = null

    private var receiverJob: Job? = null

    override suspend fun start(): Result<Unit> {
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

    override suspend fun send(packet: Packet): Result<Unit> {
        return sendChannel?.doSend(packet)
            ?: Result.failure(ConnectionException("Not connected to ${config.host}:${config.port}"))
    }

    override suspend fun disconnect() {
        socket?.let {
            socket = null
            it.close()
        }
        disconnected()
    }

    override fun close() {
        selectorManager.close()
        scope.cancel()
    }

    override fun toString(): String {
        return "DefaultMqttEngine[${config.host}:${config.port}]"
    }

    // --- Private methods ---------------------------------------------------------------------------------------------

    private suspend fun openSocket(): Socket {
        return with(config) {
            val tlsConfig = tlsConfigBuilder?.build()
            if (tlsConfig != null) {
                // We must provide our own exception handler for the TLS connection, otherwise errors (which might happen
                // due to an already closed connection) will get propagated to the parent's coroutine, which is not what
                // we want.
                val handler = CoroutineExceptionHandler { _, exception ->
                    if (connected.value) {
                        Logger.e(throwable = exception) { "TLS error while connected to $host:$port, disconnecting..." }
                        scope.launch {
                            disconnect()
                        }
                    }
                    // When not connected, ignore this exception, as it is a result of being disconnected
                }
                val tlsContext = CoroutineName("TLS Handler") + config.dispatcher + handler

                withTimeout(connectionTimeout.inWholeMilliseconds) {
                    aSocket(selectorManager).tcp().connect(host, port, tcpOptions).tls(tlsContext, tlsConfig)
                }
            } else {
                withTimeout(connectionTimeout.inWholeMilliseconds) {
                    aSocket(selectorManager).tcp().connect(host, port, tcpOptions)
                }
            }
        }
    }

    private suspend fun ByteReadChannel.incomingMessageLoop() {
        while (receiverJob?.isActive == true) {
            try {
                _packetResults.emit(Result.success(readPacket()))
            } catch (ex: CancellationException) {
                Logger.v { "Packet reader job has been cancelled, terminating..." }
                break
            } catch (ex: ClosedReceiveChannelException) {
                Logger.v { "Read channel has been closed, terminating..." }
                break
            } catch (ex: EOFException) {
                Logger.v { "End of stream detected, terminating..." }
                break
            } catch (ex: MalformedPacketException) {
                // Continue with the loop, so that the client can decide what to do
                _packetResults.emit(Result.failure(ex))
            } catch (ex: Exception) {
                Logger.w(throwable = ex) { "Read channel error detected, terminating..." }
                break
            }
        }

        Logger.d { "Incoming message loop terminated" }
        disconnected()
    }

    private suspend fun ByteWriteChannel.doSend(packet: Packet): Result<Unit> {
        Logger.d { "Sending $packet..." }

        return try {
            write(packet)
            flush()
            Result.success(Unit)
        } catch (ex: CancellationException) {
            Logger.v { "Packet writer job has been cancelled during write operation" }
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
        sendChannel?.flushAndClose()

        receiverJob = null
        socket = null
        sendChannel = null
    }
}