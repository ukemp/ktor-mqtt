package de.kempmobil.ktor.mqtt

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun createClient(id: String, configurator: MqttClientConfigBuilder<MqttEngineConfig>.() -> Unit): MqttClient? {
    val server = getenv("MQTT_SERVER")?.toKString()
    return if (server != null) {
        MqttClient(server, 1883) {
            clientId = id
            configurator()
        }
    } else {
        println("WARNING: Cannot execute Native integration test, missing ENVIRONMENT variable MQTT_SERVER")
        null
    }
}