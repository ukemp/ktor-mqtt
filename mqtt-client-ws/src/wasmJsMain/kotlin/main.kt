import de.kempmobil.ktor.mqtt.Topic
import de.kempmobil.ktor.mqtt.TopicFilter
import de.kempmobil.ktor.mqtt.ws.MqttClient
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.dom.appendElement
import kotlinx.dom.appendText
import org.w3c.dom.Element

private const val mosquitto = "test.mosquitto.org"
private const val port = 8091

private val mainScope = CoroutineScope(Dispatchers.Default)

public fun main() {
    document.body?.appendMosquitto()
}

private fun Element.appendMosquitto() {
    appendElement("div") {
        val url = Url("wss://$mosquitto:$port")
        val client = MqttClient(url) {
            username = "ro"
            password = "readonly"
        }
        appendText("Mosquitto Test to $url")
        val status = appendElement("div") {
            appendText("Connecting...")
        }
        var packetCount = 0
        val counterNode = appendElement("div") {
            appendText("Packets received: 0")
        }

        // Launch a coroutine in the main scope to handle the async operations.
        mainScope.launch {
            val connectResult = client.connect()
            if (connectResult.isSuccess) {
                status.textContent = "Connection successful. Subscribing to all topics (#)..."
                client.subscribe(listOf(TopicFilter(Topic("#"))))
                status.textContent = "Subscribed successfully. Listening for packets..."
                client.publishedPackets.collect {
                    packetCount++
                    println("Packets received: $packetCount")
                    counterNode.textContent = "Packets received: $packetCount, last topic: ${it.topic.name}"
                }
            } else {
                connectResult.exceptionOrNull()?.printStackTrace()
                status.textContent = "Connection failed: ${connectResult.exceptionOrNull()?.message}"
            }
        }
    }
}