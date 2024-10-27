package de.kempmobil.ktor.mqtt

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*

internal class WebSocketEngineFactory(private val url: Url) : MqttEngineFactory<WebSocketEngineConfig> {

    override fun create(block: WebSocketEngineConfig.() -> Unit): MqttEngine {
        return WebSocketEngine(WebSocketEngineConfig(url).apply(block))
    }
}

public class WebSocketEngineConfig(public val url: Url) : MqttEngineConfig() {

    public var http: () -> HttpClient = {
        HttpClient {
            install(WebSockets)
        }
    }
}