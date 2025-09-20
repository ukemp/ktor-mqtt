package de.kempmobil.ktor.mqtt

actual fun createClient(id: String, configurator: MqttClientConfigBuilder<MqttEngineConfig>.() -> Unit): MqttClient? {
    // TODO: find a way to configure the server name for wasmJs
    return null
}