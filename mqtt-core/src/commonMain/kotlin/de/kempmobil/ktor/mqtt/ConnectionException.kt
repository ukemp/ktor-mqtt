package de.kempmobil.ktor.mqtt

public class ConnectionException(cause: Throwable) : MqttException(cause = cause) {
}