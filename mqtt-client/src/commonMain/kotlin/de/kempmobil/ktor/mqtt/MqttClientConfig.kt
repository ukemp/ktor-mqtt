package de.kempmobil.ktor.mqtt

import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


public class MqttClientConfig(
    public val host: String,
    public val port: Int,
    public val dispatcher: CoroutineDispatcher,
    public val clientId: String,
    public val ackMessageTimeout: Duration,
    public val willMessage: WillMessage?,
    public val willOqS: QoS,
    public val retainWillMessage: Boolean,
    public val keepAliveSeconds: UShort,
    public val userName: String? = null,
    public val password: String? = null,
    public val sessionExpiryInterval: SessionExpiryInterval? = null,
    public val receiveMaximum: ReceiveMaximum? = null,
    public val maximumPacketSize: MaximumPacketSize? = null,
    public val topicAliasMaximum: TopicAliasMaximum,
    public val requestResponseInformation: RequestResponseInformation,
    public val requestProblemInformation: RequestProblemInformation,
    public val authenticationMethod: AuthenticationMethod? = null,
    public val authenticationData: AuthenticationData? = null,
    public val userProperties: UserProperties,
    public val tcpOptions: (SocketOptions.TCPClientSocketOptions.() -> Unit),
    public val tlsConfig: TLSConfigBuilder?
)

public class MqttClientConfigBuilder(
    public val host: String,
    public var port: Int = 1883
) {
    private var userPropertiesBuilder: UserPropertiesBuilder? = null
    private var willMessageBuilder: WillMessageBuilder? = null
    private var tlsConfigBuilder: TLSConfigBuilder? = null
    private var tcpOptions: (SocketOptions.TCPClientSocketOptions.() -> Unit)? = null

    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
    public var ackMessageTimeout: Duration = 7.seconds
    public var clientId: String = generateClientId()
    public var willOqS: QoS = QoS.AT_MOST_ONCE
    public var retainWillMessage: Boolean = false
    public var keepAliveSeconds: UShort = 0u
    public var userName: String? = null
    public var password: String? = null
    public var sessionExpiryInterval: SessionExpiryInterval? = null
    public var receiveMaximum: ReceiveMaximum? = null
    public var maximumPacketSize: MaximumPacketSize? = null
    public var topicAliasMaximum: TopicAliasMaximum = TopicAliasMaximum(0u)
    public var requestResponseInformation: Boolean = false
    public var requestProblemInformation: Boolean = true
    public var authenticationMethod: AuthenticationMethod? = null
    public var authenticationData: AuthenticationData? = null

    /**
     * Build user properties used in the CONNECT packet of this client.
     */
    public fun userProperties(init: UserPropertiesBuilder.() -> Unit) {
        userPropertiesBuilder = UserPropertiesBuilder().also(init)
    }

    /**
     * Build the last will message for this client.
     */
    public fun willMessage(topic: String, init: WillMessageBuilder.() -> Unit) {
        willMessageBuilder = WillMessageBuilder(topic).also(init)
    }

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

    public fun build(): MqttClientConfig = MqttClientConfig(
        host = host,
        port = port,
        dispatcher = dispatcher,
        clientId = clientId,
        ackMessageTimeout = ackMessageTimeout,
        willMessage = willMessageBuilder?.build(),
        willOqS = willOqS,
        retainWillMessage = retainWillMessage,
        keepAliveSeconds = keepAliveSeconds,
        userName = userName,
        password = password,
        sessionExpiryInterval = sessionExpiryInterval,
        receiveMaximum = receiveMaximum,
        maximumPacketSize = maximumPacketSize,
        topicAliasMaximum = topicAliasMaximum,
        requestResponseInformation = RequestResponseInformation(requestResponseInformation),
        requestProblemInformation = RequestProblemInformation(requestProblemInformation),
        authenticationMethod = authenticationMethod,
        authenticationData = authenticationData,
        userProperties = userPropertiesBuilder?.build() ?: UserProperties.EMPTY,
        tcpOptions = tcpOptions ?: { },
        tlsConfig = tlsConfigBuilder
    )
}

private fun generateClientId(): String {
    val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    return buildString(23) {
        repeat(23) {
            append(chars[Random.nextInt(chars.length)])
        }
    }
}