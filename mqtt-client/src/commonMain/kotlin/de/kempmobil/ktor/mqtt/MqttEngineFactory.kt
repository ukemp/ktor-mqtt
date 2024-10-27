package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.MqttDslMarker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

public interface MqttEngineFactory<out T : MqttEngineConfig> {

    public fun create(block: T.() -> Unit): MqttEngine
}

@MqttDslMarker
public open class MqttEngineConfig {

    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
}