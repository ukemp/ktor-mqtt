package de.kempmobil.ktor.mqtt.ws

import de.kempmobil.ktor.mqtt.MqttClient
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

open class DemoServer(
    val host: String,
    val port: Int,
    val tlsPort: Int,
    val websocketPort: Int,
    val tlsWebsocketPort: Int
) {
    open val clientId: String
        get() = "${this::class.simpleName}-${Random.nextInt(1_000, Int.MAX_VALUE)}"

    open val websocketPath = "/mqtt"
}

object Mosquitto : DemoServer("test.mosquitto.org", 1883, 8886, 8080, 8081)
object HiveMQ : DemoServer("broker.hivemq.com", 1883, 8883, 8000, 8884)
object Emqx : DemoServer("broker.emqx.io", 1883, 8883, 8083, 8084)

@Ignore
class PublicBrokerTest {

    private val server: DemoServer = Emqx

    @Test
    fun `test unencrypted connection`() = runTest {
        MqttClient(server.host, server.port) {
            clientId = server.clientId
        }.testConnection()
    }

    @Test
    fun `test encrypted connection`() = runTest {
        MqttClient(server.host, server.tlsPort) {
            clientId = server.clientId
            connection {
                tls { }
            }
        }.testConnection()
    }

    @Test
    fun `test unencrypted websocket connection`() = runTest {
        MqttClient(Url("ws://${server.host}:${server.websocketPort}${server.websocketPath}")) {
            clientId = server.clientId
        }.testConnection()
    }

    @Test
    fun `test encrypted websocket connection`() = runTest {
        MqttClient(Url("https://${server.host}:${server.tlsWebsocketPort}${server.websocketPath}")) {
            clientId = server.clientId
        }.testConnection()
    }

    private suspend fun MqttClient.testConnection() {
        connect().onSuccess { connack ->
            println("Received $connack")
            assertEquals(true, connack.isSuccess)
        }.onFailure { throwable ->
            throwable.printStackTrace()
            fail("Error connecting to server")
        }

        disconnect()
    }
}