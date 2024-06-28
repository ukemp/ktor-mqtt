package de.kempmobil.ktor.mqtt

import io.ktor.network.selector.*

public class MqttClient(private val config: MqttClientConfig) {

    private val selectorManager = SelectorManager(config.dispatcher)

    public fun start() {

    }
}

public fun MqttClient(host: String, port: Int, init: MqttClientConfigBuilder.() -> Unit): MqttClient {
    return MqttClient(MqttClientConfigBuilder(host, port).also(init).build())
}