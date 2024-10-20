package de.kempmobil.ktor.mqtt

import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal object DefaultConfig : MqttEngineFactory<DefaultEngineConfig> {

    override fun create(host: String, port: Int, block: DefaultEngineConfig.() -> Unit): MqttEngine {
        return DefaultMqttEngine(DefaultEngineConfig(host, port).apply(block))
    }
}

public class DefaultEngineConfig(host: String, port: Int) : MqttEngineConfig(host, port) {
    public val dispatcher: CoroutineDispatcher = Dispatchers.Default
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