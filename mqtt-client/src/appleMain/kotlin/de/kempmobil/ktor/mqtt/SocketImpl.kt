package de.kempmobil.ktor.mqtt

import cocoapods.SocketWrapper.ClientSocketWrapper
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformWhile
import kotlin.time.Duration

@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)
class NWClientSocketWrapper(val useTls: Boolean) : NWSocketWrapper(), ClientToServerSocket {
    private val callbackStateFlow by lazy(LazyThreadSafetyMode.NONE) {
        callbackFlow {
            val socket = checkNotNull(socket as? ClientSocketWrapper)
            socket.setStateUpdateHandlerWithHandler { _, state, error, _, dnsError, _ ->
                if (channel.isClosedForSend) {
                    return@setStateUpdateHandlerWithHandler
                }
                if (state!!.startsWith("ready")) {
                    trySendBlocking(true).getOrThrow()
                } else if (state.startsWith("failed") ||
                    state.startsWith("cancelled") ||
                    state.startsWith("waiting")
                ) {
                    if (error != null) {
                        if (dnsError) {
                            channel.close(SocketUnknownHostException(error))
                        } else {
                            channel.close(SocketException(error))
                        }
                    } else {
                        channel.close()
                    }
                } else {
                    trySendBlocking(false).getOrThrow()
                }
            }
            awaitClose()
        }
    }

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
    override suspend fun open(
        port: Int,
        timeout: Duration,
        hostname: String?,
    ) {
        val socket =
            ClientSocketWrapper(
                hostname ?: "localhost",
                port.toUShort(),
                timeout.inWholeSeconds.convert(),
                useTls,
            )
        this.socket = socket
        socket.start()
        awaitConnectionOrThrow()
    }

    private suspend fun awaitConnectionOrThrow() {
        if (isOpen()) return
        callbackStateFlow
            // keep the flow active until we reach the true state
            .transformWhile<Boolean, Boolean> { !it }
            .collect()
    }
}