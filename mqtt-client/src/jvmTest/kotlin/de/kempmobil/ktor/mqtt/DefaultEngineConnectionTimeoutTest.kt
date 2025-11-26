package de.kempmobil.ktor.mqtt

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DefaultEngineConnectionTimeoutTest {

    // Test is in the JVM package as it won't work with WASM (only WebSockets) and it doesn't work in iOS (missing
    // support for secure sockets).

    @Test
    fun `ensure connection times out after connection timeout`() = runTest(timeout = 2.seconds) {
        testConnectionTimeout { port ->
            DefaultEngineConfig("localhost", port).apply {
                connectionTimeout = 100.milliseconds
            }
        }
    }

    @Test
    fun `ensure TLS connection times out after connection timeout`() = runTest(timeout = 2.seconds) {
        testConnectionTimeout { port ->
            DefaultEngineConfig("localhost", port).apply {
                connectionTimeout = 100.milliseconds
                tls { }
            }
        }
    }

    private suspend fun testConnectionTimeout(createEngineConfig: (Int) -> DefaultEngineConfig) = coroutineScope {
        // Create a server socket which will not accept the connection from our DefaultEngine
        val selectorManager = SelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager).tcp().bind(hostname = "127.0.0.1", port = 0) {
            backlogSize = 1
        }
        val port = (serverSocket.localAddress as InetSocketAddress).port
        val barrier = MutableStateFlow(false)

        val blockServerSocketJob = launch {
            try {
                // The first connections succeeds
                aSocket(selectorManager).tcp().connect("127.0.0.1", port)
                // The second connection congests the backlog
                aSocket(selectorManager).tcp().connect("127.0.0.1", port)
                barrier.value = true

                suspendCancellableCoroutine { }
            } finally {
                serverSocket.close()
            }
        }

        barrier.first { it }

        val engine = DefaultEngine(createEngineConfig(port))
        val connected = engine.start()

        assertFalse(connected.isSuccess, "Connection should not be successful")
        assertIs<ConnectionException>(connected.exceptionOrNull())

        // Clean up the server coroutine
        blockServerSocketJob.cancelAndJoin()
    }
}