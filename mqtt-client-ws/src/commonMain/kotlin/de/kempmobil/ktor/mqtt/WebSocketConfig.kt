package de.kempmobil.ktor.mqtt

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal object WebSocketConfig : MqttEngineFactory<WebSocketEngineConfig> {

    override fun create(host: String, port: Int, block: WebSocketEngineConfig.() -> Unit): MqttEngine {
        return WebSocketMqttEngine(WebSocketEngineConfig(host, port).apply(block))
    }
}

public class WebSocketEngineConfig(host: String, port: Int) : MqttEngineConfig(host, port) {

    public val dispatcher: CoroutineDispatcher = Dispatchers.Default

    public var client: HttpClient = HttpClient(CIO)

    internal var tlsConfigBuilder: TLSConfigBuilder? = null

    internal var tcpOptions: (SocketOptions.TCPClientSocketOptions.() -> Unit) = { }


    /**
     * Add TLS configuration for this client. Just use `tls { }` to enable TLS support.
     */
    public fun tls(init: TLSConfigBuilder.() -> Unit) {
        tlsConfigBuilder = TLSConfigBuilder().also(init)
    }

    /**
     * Configure the TCP options for this client
     *
     * @see SocketOptions.TCPClientSocketOptions
     */
    public fun tcp(init: SocketOptions.TCPClientSocketOptions.() -> Unit) {
        tcpOptions = init
    }
}