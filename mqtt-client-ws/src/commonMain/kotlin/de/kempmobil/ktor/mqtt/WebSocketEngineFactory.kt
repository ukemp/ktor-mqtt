package de.kempmobil.ktor.mqtt

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal object WebSocketEngineFactory : MqttEngineFactory<WebSocketEngineConfig> {

    override fun create(host: String, port: Int, block: WebSocketEngineConfig.() -> Unit): MqttEngine {
        return WebSocketEngine(WebSocketEngineConfig(host, port).apply(block))
    }
}

public class WebSocketEngineConfig(host: String, port: Int) : MqttEngineConfig(host, port) {

    public var dispatcher: CoroutineDispatcher = Dispatchers.Default

    public var clientFactory: () -> HttpClient = {
        HttpClient(CIO) {
            install(WebSockets)
        }
    }

    /**
     * When `true` use WSS (a.k.a TLS) as the web socket protocol, else don't use encryption.
     */
    public var useWss: Boolean = false

    /**
     * The websocket path to connect to, defaults to "".
     */
    public var path: String = ""
}