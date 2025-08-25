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
import org.w3c.dom.HTMLButtonElement

private val url = Url("wss://test.mosquitto.org:8091")
private val mainScope = CoroutineScope(Dispatchers.Default)
private var packetCount = 0
private var connected = false

public fun main() {
    document.body?.appendMosquitto()
}

private fun Element.appendMosquitto() {
    val client = MqttClient(url) {
        username = "ro"
        password = "readonly"
    }

    appendElement("div") {
        appendElement("h3") {
            appendText("Ktor-MQTT WASM Test Page")
        }
        appendElement("p") {
            appendElement("div") { appendText("When pressing start, connect to $url") }
            appendElement("div") { appendText("and subscribe to all topics.") }
        }
        val startStop = appendElement("button") {
            this as HTMLButtonElement
            type = "button"
        }
        val status = appendElement("div") {
            appendText("")
        }
        val counterNode = appendElement("div") {
            appendText("Packets received: 0")
        }
        startStop.addEventListener("click") {
            if (connected) {
                mainScope.launch {
                    client.disconnect()
                }
            } else {
                packetCount = 0
                mainScope.launch {
                    val connectResult = client.connect()
                    if (connectResult.isSuccess) {
                        client.subscribe(listOf(TopicFilter(Topic("#"))))
                    } else {
                        connectResult.exceptionOrNull()?.printStackTrace()
                        status.textContent = "Connection failed: ${connectResult.exceptionOrNull()?.message}"
                    }
                }
            }
        }

        mainScope.launch {
            client.connectionState.collect {
                if (it.isConnected) {
                    status.textContent = "Connected"
                    connected = true
                    (startStop as HTMLButtonElement).textContent = "Stop"
                } else {
                    status.textContent = "Not connected"
                    connected = false
                    (startStop as HTMLButtonElement).textContent = "Start"
                }
            }
        }
        mainScope.launch {
            client.publishedPackets.collect {
                packetCount++
                counterNode.textContent = "Packets received: $packetCount, last topic: ${it.topic.name}"
            }
        }
    }
}