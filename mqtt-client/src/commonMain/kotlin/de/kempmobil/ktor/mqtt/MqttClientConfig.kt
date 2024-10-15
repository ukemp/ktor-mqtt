package de.kempmobil.ktor.mqtt

import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * Mqtt client configuration, see [buildConfig]
 *
 * @property host name of the MQTT server to connect to
 * @property port port of the MQTT server to connect to
 * @property dispatcher the coroutine dispatcher to use for background tasks
 * @property clientId the ID of this client, use an empty string to receive a client ID from the server
 * @property ackMessageTimeout the time to wait for an acknowledge message from the server, defaults to 7 seconds
 * @property willMessage the MQTT last will message or `null` if non should be used
 * @property willOqS the QoS of the last will message
 * @property retainWillMessage the value of bit 5 (will retain) in the connect message of this client
 * @property keepAliveSeconds the value of keep alive in the connect message of this client
 * @property username the username for authenticating this client
 * @property password the password of the user
 * @property sessionExpiryInterval the value of the session expiry interval of the connect message of this client
 * @property receiveMaximum the receive maximum of this client for QoS 1 or 2
 * @property maximumPacketSize the maximum packet size this client is willing to accept
 * @property topicAliasMaximum indicates the highest value that the Client will accept as a Topic Alias sent by the server
 * @property requestResponseInformation request the server to return Response Information in the CONNACK
 * @property requestProblemInformation use this value to indicate whether the reason string or user properties are sent in the case of failures
 * @property authenticationMethod currently not used
 * @property authenticationData currently not used
 * @property userProperties the user properties used in the Connect packet
 * @property tcpOptions optional block for configuring the TCP options of the connection to the server
 * @property tlsConfigBuilder the Ktor TLS configuration
 */
public interface MqttClientConfig {
    public val host: String
    public val port: Int
    public val dispatcher: CoroutineDispatcher
    public val clientId: String
    public val ackMessageTimeout: Duration
    public val willMessage: WillMessage?
    public val willOqS: QoS
    public val retainWillMessage: Boolean
    public val keepAliveSeconds: UShort
    public val username: String?
    public val password: String?
    public val sessionExpiryInterval: SessionExpiryInterval?
    public val receiveMaximum: ReceiveMaximum?
    public val maximumPacketSize: MaximumPacketSize?
    public val topicAliasMaximum: TopicAliasMaximum
    public val requestResponseInformation: RequestResponseInformation
    public val requestProblemInformation: RequestProblemInformation
    public val authenticationMethod: AuthenticationMethod?
    public val authenticationData: AuthenticationData?
    public val userProperties: UserProperties
    public val tcpOptions: (SocketOptions.TCPClientSocketOptions.() -> Unit)
    public val tlsConfigBuilder: TLSConfigBuilder?
}

/**
 * DSL for creating an MQTT client configuration.
 *
 * @param host the host to connect to
 * @param port the port to connect to, defaults to 1883
 * @sample dslSample
 */
public fun buildConfig(host: String, port: Int = 1883, init: MqttClientConfigBuilder.() -> Unit): MqttClientConfig {
    return MqttClientConfigBuilder(host, port).also(init).build()
}

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
    public var username: String? = null
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

    public fun build(): MqttClientConfig = MqttClientConfigImpl(
        host = host,
        port = port,
        dispatcher = dispatcher,
        clientId = clientId,
        ackMessageTimeout = ackMessageTimeout,
        willMessage = willMessageBuilder?.build(),
        willOqS = willOqS,
        retainWillMessage = retainWillMessage,
        keepAliveSeconds = keepAliveSeconds,
        username = username,
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
        tlsConfigBuilder = tlsConfigBuilder
    )
}

private fun dslSample() {
    buildConfig("test.mosquitto.org", 8883) {
        clientId = "test-client"
        username = "test-user"
        password = "12345678"
        willMessage("topics/last-will") {
            payload("Last will message of test-client")
            properties {
                messageExpiryInterval = 120u
            }
        }
        userProperties {
            "user-key" to "value1"
            "user-key" to "value2"  // Properties keys may occur more than once
        }
        tcp {
            noDelay = true
            lingerSeconds = 10
        }
        tls { }  // Enable TLS with system trust manager
    }
}

private class MqttClientConfigImpl(
    override val host: String,
    override val port: Int,
    override val dispatcher: CoroutineDispatcher,
    override val clientId: String,
    override val ackMessageTimeout: Duration,
    override val willMessage: WillMessage?,
    override val willOqS: QoS,
    override val retainWillMessage: Boolean,
    override val keepAliveSeconds: UShort,
    override val username: String? = null,
    override val password: String? = null,
    override val sessionExpiryInterval: SessionExpiryInterval? = null,
    override val receiveMaximum: ReceiveMaximum? = null,
    override val maximumPacketSize: MaximumPacketSize? = null,
    override val topicAliasMaximum: TopicAliasMaximum,
    override val requestResponseInformation: RequestResponseInformation,
    override val requestProblemInformation: RequestProblemInformation,
    override val authenticationMethod: AuthenticationMethod? = null,
    override val authenticationData: AuthenticationData? = null,
    override val userProperties: UserProperties,
    override val tcpOptions: (SocketOptions.TCPClientSocketOptions.() -> Unit),
    override val tlsConfigBuilder: TLSConfigBuilder?
) : MqttClientConfig

private fun generateClientId(): String {
    val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    return buildString(23) {
        repeat(23) {
            append(chars[Random.nextInt(chars.length)])
        }
    }
}