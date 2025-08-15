package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.MutableLoggerConfig
import de.kempmobil.ktor.mqtt.util.Logger
import de.kempmobil.ktor.mqtt.util.MqttDslMarker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.io.bytestring.ByteString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * Mqtt client configuration, see [buildConfig]
 */
public interface MqttClientConfig {
    public val engine: MqttEngine
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
}

/**
 * DSL for creating an MQTT client configuration.
 *
 * @param connectionFactory the connection factory to use, usually [DefaultEngineFactory].
 * @param init DSL for configuring the client
 */
public fun <T : MqttEngineConfig> buildConfig(
    connectionFactory: MqttEngineFactory<T>,
    init: MqttClientConfigBuilder<T>.() -> Unit
): MqttClientConfig {
    return MqttClientConfigBuilder(connectionFactory).also(init).build()
}

/**
 * Mqtt client config builder
 *
 * @property dispatcher the coroutine dispatcher to use for background tasks
 * @property ackMessageTimeout the time to wait for an acknowledgment/handshake messages from the server, defaults to 7 seconds
 * @property clientId the ID of this client, defaults to an empty string
 * @property keepAliveSeconds the value of keep alive in the connect message of this client, defaults to 0
 * @property username the username for authenticating this client
 * @property password the password of the user
 * @property sessionExpiryInterval the value of the session expiry interval of the connect message of this client
 * @property receiveMaximum limits the number of QoS 1 and QoS 2 publications that this client is willing to process concurrently
 * @property maximumPacketSize the maximum packet size this client is willing to accept
 * @property topicAliasMaximum indicates the highest value that the Client will accept as a Topic Alias sent by the server, default: 0
 * @property requestResponseInformation request the server to return Response Information in the CONNACK, default `false`
 * @property requestProblemInformation use this value to indicate whether the reason string or user properties are sent in the case of failures, default: `true`
 * @property authenticationMethod currently not used
 * @property authenticationData currently not used
 */
@MqttDslMarker
@Suppress("MemberVisibilityCanBePrivate")
public class MqttClientConfigBuilder<out T : MqttEngineConfig>(
    private val engineFactory: MqttEngineFactory<T>
) {
    private var userPropertiesBuilder: UserPropertiesBuilder? = null
    private var willMessageBuilder: WillMessageBuilder? = null
    private var engine: MqttEngine? = null
    private var loggerConfig: (MutableLoggerConfig.() -> Unit)? = null

    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
    public var ackMessageTimeout: Duration = 7.seconds
    public var clientId: String = ""
    public var keepAliveSeconds: UShort = 0u
    public var username: String? = null
    public var password: String? = null
    public var sessionExpiryInterval: Duration? = null
    public var receiveMaximum: UShort? = null
    public var maximumPacketSize: UInt? = null
    public var topicAliasMaximum: UShort = 0u
    public var requestResponseInformation: Boolean = false
    public var requestProblemInformation: Boolean = true
    public var authenticationMethod: String? = null
    public var authenticationData: ByteString? = null

    public fun connection(init: T.() -> Unit) {
        engine = engineFactory.create(init)
    }

    public fun logging(init: MutableLoggerConfig.() -> Unit) {
        loggerConfig = init
    }

    /**
     * Build user properties used in the CONNECT packet of this client.
     */
    public fun userProperties(init: UserPropertiesBuilder.() -> Unit) {
        userPropertiesBuilder = UserPropertiesBuilder().also(init)
    }

    /**
     * Build the last will message for this client.
     *
     * @param topic the topic name of the last will message of this client
     */
    public fun willMessage(topic: String, init: WillMessageBuilder.() -> Unit) {
        willMessageBuilder = WillMessageBuilder(topic).also(init)
    }

    public fun build(): MqttClientConfig {
        loggerConfig?.let {
            Logger.configureLogging(it)
        }
        if (engine == null) {
            engine = engineFactory.create { }
        }
        return MqttClientConfigImpl(
            engine = engine!!,
            dispatcher = dispatcher,
            clientId = clientId,
            ackMessageTimeout = ackMessageTimeout,
            willMessage = willMessageBuilder?.build(),
            willOqS = willMessageBuilder?.willOqS ?: QoS.AT_MOST_ONCE,
            retainWillMessage = willMessageBuilder?.retainWillMessage ?: false,
            keepAliveSeconds = keepAliveSeconds,
            username = username,
            password = password,
            sessionExpiryInterval = sessionExpiryInterval?.let { SessionExpiryInterval(it.inWholeSeconds.toUInt()) },
            receiveMaximum = receiveMaximum?.let { ReceiveMaximum(it) },
            maximumPacketSize = maximumPacketSize?.let { MaximumPacketSize(it) },
            topicAliasMaximum = TopicAliasMaximum(topicAliasMaximum),
            requestResponseInformation = RequestResponseInformation(requestResponseInformation),
            requestProblemInformation = RequestProblemInformation(requestProblemInformation),
            authenticationMethod = authenticationMethod?.let { AuthenticationMethod(it) },
            authenticationData = authenticationData?.let { AuthenticationData(it) },
            userProperties = userPropertiesBuilder?.build() ?: UserProperties.EMPTY,
        )
    }
}

private class MqttClientConfigImpl(
    override val engine: MqttEngine,
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
) : MqttClientConfig
