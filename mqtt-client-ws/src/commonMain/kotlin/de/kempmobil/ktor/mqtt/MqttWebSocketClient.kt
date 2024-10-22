package de.kempmobil.ktor.mqtt

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Creates a new MQTT client using web sockets for connecting to the server.
 *
 * @sample createClientDsl
 */
@Suppress("FunctionName")
public fun MqttWebSocketClient(init: MqttClientConfigBuilder<WebSocketEngineConfig>.() -> Unit): MqttClient {
    return MqttClient(MqttClientConfigBuilder(WebSocketEngineFactory).also(init).build())
}

private fun createClientDsl() {
    val client = MqttWebSocketClient {
        connectTo("test.mosquitto.org", 8091) {
            clientFactory = {
                HttpClient(CIO) {
                    install(WebSockets)
                }
            }
        }

        clientId = "test-client"
        username = "ro"
        password = "readonly"

        willMessage("topics/last-will") {
            retainWillMessage = true
            willOqS = QoS.AT_MOST_ONCE
            payload("Last will message of test-client")
            properties {
                willDelayInterval = 10.seconds
                messageExpiryInterval = 2.days
            }
        }
        userProperties {
            "user-key" to "value1"
            "user-key" to "value2"  // Property keys may occur more than once!
        }
    }
}
