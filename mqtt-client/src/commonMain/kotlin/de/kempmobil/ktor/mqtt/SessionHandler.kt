package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class SessionHandler(private val config: MqttClientConfig) : PacketReceiver {

    private val selectorManager = SelectorManager(config.dispatcher)

    private val outPackets = MutableSharedFlow<Packet>(replay = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var socket: Socket? = null

    private var incomingJob: Job? = null

    private var outgoingJob: Job? = null

    private var packetIdentifier: UShort = 1u

    private val isCleanStart: Boolean
        get() = true // TODO

    internal fun start() {
        runBlocking {
            socket = openSocket().also { socket ->
                incomingJob = launch { socket.openReadChannel().incomingMessageLoop() }
                outgoingJob = launch { socket.openWriteChannel().outgoingMessagesLoop() }
            }
            outPackets.emit(createConnect())
        }
    }

    internal fun stop() {
        incomingJob?.cancel()
        outgoingJob?.cancel()
        socket?.close()
        selectorManager.close()
    }

    // --- Private methods ---------------------------------------------------------------------------------------------

    private suspend fun openSocket(): Socket {
        val socket = aSocket(selectorManager).tcp().connect(config.host, port = config.port)
        config.tlsConfig?.let { tlsConfigBuilder ->
            socket.tls(config.dispatcher) {
                takeFrom(tlsConfigBuilder)
            }
        }
        return socket
    }

    private suspend fun ByteReadChannel.incomingMessageLoop() {
        try {
            while (true) {
                readPacket(this@SessionHandler)
            }
        } catch (ex: CancellationException) {
            Logger.d { "Packet reader job has been cancelled" }
        }
    }

    private suspend fun ByteWriteChannel.outgoingMessagesLoop() {
        try {
            outPackets.collect { packet ->
                write(packet)
            }
        } catch (ex: CancellationException) {
            Logger.d { "Packet writer job has been cancelled" }
        }
    }

    private fun createConnect(): Connect {
        return Connect(
            isCleanStart = isCleanStart,
            willMessage = config.willMessage,
            willOqS = config.willOqS,
            retainWillMessage = config.retainWillMessage,
            keepAliveSeconds = config.keepAliveSeconds,
            clientId = config.clientId,
            userName = config.userName,
            password = config.password,
            sessionExpiryInterval = config.sessionExpiryInterval,
            receiveMaximum = config.receiveMaximum,
            maximumPacketSize = config.maximumPacketSize,
            topicAliasMaximum = config.topicAliasMaximum,
            requestResponseInformation = config.requestResponseInformation,
            requestProblemInformation = config.requestProblemInformation,
            userProperties = config.userProperties,
            authenticationMethod = config.authenticationMethod,
            authenticationData = config.authenticationData
        )
    }

    private fun nextPacketIdentifier(): UShort {
        packetIdentifier = (packetIdentifier + 1u).toUShort()
        if (packetIdentifier == 0u.toUShort()) {
            packetIdentifier = 1u
        }
        return packetIdentifier
    }

    // ---- Interface methods ------------------------------------------------------------------------------------------

    override fun onConnect(connect: Connect) {
        Logger.w { "Illegal client packet received: $connect" }
    }

    override fun onConnack(connack: Connack) {
        Logger.v { "New packet received: $connack" }
    }

    override fun onPublish(publish: Publish) {
        Logger.v { "New packet received: $publish" }
    }

    override fun onPuback(puback: Puback) {
        Logger.v { "New packet received: $puback" }
    }

    override fun onPubrec(pubrec: Pubrec) {
        Logger.v { "New packet received: $pubrec" }
    }

    override fun onPubrel(pubrel: Pubrel) {
        Logger.v { "New packet received: $pubrel" }
    }

    override fun onPubcomp(pubcomp: Pubcomp) {
        Logger.v { "New packet received: $pubcomp" }
    }

    override fun onSubscribe(subscribe: Subscribe) {
        Logger.w { "Illegal client packet received: $subscribe" }
    }

    override fun onSuback(suback: Suback) {
        Logger.v { "New packet received: $suback" }
    }

    override fun onUnsubscribe(unsubscribe: Unsubscribe) {
        Logger.w { "Illegal client packet received: $unsubscribe" }
    }

    override fun onUnsuback(unsuback: Unsuback) {
        Logger.v { "New packet received: $unsuback" }
    }

    override fun onPingreq() {
        Logger.w { "Illegal client packet received: PINGREQ" }
    }

    override fun onPingresp() {
        Logger.v { "New packet received: PINGRESP" }
    }

    override fun onDisconnect(disconnect: Disconnect) {
        Logger.v { "New packet received: $disconnect" }
    }

    override fun onAuth(auth: Auth) {
        Logger.v { "New packet received: $auth" }
    }

    override fun onMalformedPacket(exception: MalformedPacketException) {
        Logger.e(throwable = exception) { "Malformed packet received" }
    }
}