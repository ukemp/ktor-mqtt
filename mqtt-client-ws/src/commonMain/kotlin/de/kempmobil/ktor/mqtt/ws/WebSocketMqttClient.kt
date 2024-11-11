package de.kempmobil.ktor.mqtt.ws

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.MqttClientConfigBuilder
import de.kempmobil.ktor.mqtt.QoS
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Creates a new MQTT client using web sockets for connecting to the server.
 *
 * When not configured, this will use the default ktor http client as described [here](https://ktor.io/docs/client-engines.html#default).
 * If you want to specify or configure an engine, for example CIO, use the following code snippet:
 * ```
 * MqttClient("https://test.mosquitto.org:8091") {
 *     connection {
 *         http = {
 *             HttpClient(CIO) {
 *                 install(WebSockets)
 *             }
 *         }
 *     }
 *     ...
 * }
 * ```
 *
 * @param url the URL to connect to, this may contain a 'http', 'https', 'ws' or 'wss' protocol
 * @sample WsSample.createWebsocketClient
 */
public fun MqttClient(url: String, init: MqttClientConfigBuilder<WebSocketEngineConfig>.() -> Unit): MqttClient {
    return MqttClient(Url(url), init)
}

/**
 * Creates a new MQTT client using web sockets for connecting to the server.
 *
 * When not configured, this will use the default ktor http client as described [here](https://ktor.io/docs/client-engines.html#default).
 * If you want to specify or configure an engine, for example CIO, use the following code snippet:
 * ```
 * MqttClient("https://test.mosquitto.org:8091") {
 *     connection {
 *         http = {
 *             HttpClient(CIO) {
 *                 install(WebSockets)
 *             }
 *         }
 *     }
 *     ...
 * }
 * ```
 *
 * @param url the URL to connect to, this may contain a 'http', 'https', 'ws' or 'wss' protocol
 * @sample WsSample.createWebsocketClient
 */
public fun MqttClient(url: Url, init: MqttClientConfigBuilder<WebSocketEngineConfig>.() -> Unit): MqttClient {
    return MqttClient(MqttClientConfigBuilder(WebSocketEngineFactory((url))).also(init).build())
}

internal class WsSample {

    internal fun createWebsocketClient() {
        val client = MqttClient("https://test.mosquitto.org:8091") {
            connection {
                http = {
                    HttpClient {
                        install(WebSockets)
                        engine {
                            proxy = ProxyBuilder.http("http://my.proxy.com:3128")
                        }
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
}