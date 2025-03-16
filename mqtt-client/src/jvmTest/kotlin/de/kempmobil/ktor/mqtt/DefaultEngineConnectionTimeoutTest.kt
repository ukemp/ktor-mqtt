package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.test.runTest
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class DefaultEngineConnectionTimeoutTest {

    @Test
    fun `ensure connection times out after connection timeout`() = runTest(timeout = 500.milliseconds) {
        testConnectionTimeout { port ->
            DefaultEngineConfig("localhost", port).apply {
                connectionTimeout = 100.milliseconds
            }
        }
    }

    @Test
    fun `ensure TLS connection times out after connection timeout`() = runTest(timeout = 500.milliseconds) {
        testConnectionTimeout { port ->
            DefaultEngineConfig("localhost", port).apply {
                connectionTimeout = 100.milliseconds
                tls { }
            }
        }
    }

    private suspend fun testConnectionTimeout(createEngineConfig: (Int) -> DefaultEngineConfig) {
        ServerSocket(0, 1).use { serverSocket ->
            // Fill the server backlog, so subsequent connection requests will be blocked. Why do we have to use 2
            // connections instead of just one? Don't know, currently it works, should be investigated later.
            println("Filling server backlog with connections...")
            Socket().connect(serverSocket.getLocalSocketAddress())
            Socket().connect(serverSocket.getLocalSocketAddress())
            println("Server backlog filled.")

            val engine = DefaultEngine(createEngineConfig(serverSocket.localPort))
            val connected = engine.start()

            assertFalse(connected.isSuccess)
            assertIs<ConnectionException>(connected.exceptionOrNull())
        }
    }
}