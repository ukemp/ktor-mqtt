package de.kempmobil.ktor.mqtt


public class MqttClient(private val config: MqttClientConfig) {

    private val sessionHandler = SessionHandler(config)

    public fun start() {
        sessionHandler.start()
    }

    public fun subscribe(
        filters: List<TopicFilter>,
        subscriptionIdentifier: SubscriptionIdentifier?,
        userProperties: UserProperties = UserProperties.EMPTY,
    ) {

    }
}

public fun MqttClient(host: String, port: Int, init: MqttClientConfigBuilder.() -> Unit): MqttClient {
    return MqttClient(MqttClientConfigBuilder(host, port).also(init).build())
}