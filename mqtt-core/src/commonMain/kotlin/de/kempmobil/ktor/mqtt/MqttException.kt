package de.kempmobil.ktor.mqtt

public open class MqttException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)