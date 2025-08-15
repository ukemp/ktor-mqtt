package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.ws.MqttClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue


class MosquittoOrgConnectionTest {

    @Test
    fun `all test-mosquitto-org connections are working`() = runTest {
        factories.forEach { createClient ->
            createClient().use { client ->
                val result = client.connect()
                if (result.isFailure) {
                    println(result.exceptionOrNull()?.printStackTrace())
                }
                assertTrue(result.isSuccess)
            }
        }
    }
}

interface ClientFactory {
    operator fun invoke(): MqttClient
}


// See https://test.mosquitto.org/
const val SERVER = "test.mosquitto.org"
val factories = listOf(
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating unencrypted, unauthenticated client")
            return MqttClient(SERVER, 1883) { }
        }
    },
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating unencrypted, authenticated client")
            return MqttClient(SERVER, 1884) {
                username = "ro"
                password = "readonly"
            }
        }
    },
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating encrypted, unauthenticated client")
            return MqttClient(SERVER, 8883) {
                connection {
                    tls {
                        trustManager = NoTrustManager
                    }
                }
            }
        }
    },
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating encrypted, authenticated client")
            return MqttClient(SERVER, 8885) {
                username = "ro"
                password = "readonly"
                connection {
                    tls {
                        trustManager = NoTrustManager
                    }
                }
            }
        }
    },
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating encrypted, unauthenticated client")
            return MqttClient(SERVER, 8886) {
                connection {
                    tls { }
                }
            }
        }
    },
    // --------------------------------------------  W E B   S O C K E T S ---------------------------------------------
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating unencrypted, unauthenticated websocket client")
            return MqttClient("http://$SERVER:8080") { }
        }
    },
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating encrypted, unauthenticated websocket client")
            return MqttClient("https://$SERVER:8081") { }
        }
    },
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating unencrypted, authenticated websocket client")
            return MqttClient("http://$SERVER:8090") {
                username = "ro"
                password = "readonly"
            }
        }
    },
    object : ClientFactory {
        override fun invoke(): MqttClient {
            println("Creating encrypted, authenticated websocket client")
            return MqttClient("https://$SERVER:8091") {
                username = "ro"
                password = "readonly"
            }
        }
    },
)
