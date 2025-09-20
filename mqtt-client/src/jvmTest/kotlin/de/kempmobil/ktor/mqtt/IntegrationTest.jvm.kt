package de.kempmobil.ktor.mqtt

actual fun createClient(id: String, configurator: MqttClientConfigBuilder<MqttEngineConfig>.() -> Unit): MqttClient? {
    val server = System.getenv("MQTT_SERVER")
    return if (server != null) {
        MqttClient(server, 1883) {
            clientId = id
            configurator()
        }
    } else {
        null
    }
}