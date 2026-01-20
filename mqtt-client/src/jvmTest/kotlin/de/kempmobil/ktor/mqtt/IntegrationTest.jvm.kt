package de.kempmobil.ktor.mqtt

actual fun createClient(id: String, configurator: MqttClientConfigBuilder<MqttEngineConfig>.() -> Unit): MqttClient? {
    val server = System.getenv("MQTT_SERVER")
    val port = 1883
    return if (server != null) {
        println("Creating MQTT client ($id) for: $server:$port")
        MqttClient(server, port) {
            clientId = id
            configurator()
        }
    } else {
        println("WARNING: Cannot execute JVM integration test, missing ENVIRONMENT variable MQTT_SERVER")
        null
    }
}