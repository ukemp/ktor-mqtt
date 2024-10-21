package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.MqttDslMarker

public interface MqttEngineFactory<out T : MqttEngineConfig> {

    public fun create(host: String, port: Int, block: T.() -> Unit): MqttEngine
}

@MqttDslMarker
public open class MqttEngineConfig(public val host: String, public val port: Int = 1883)