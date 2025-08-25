@file:OptIn(ExperimentalTime::class)

import de.kempmobil.ktor.mqtt.Topic
import de.kempmobil.ktor.mqtt.TopicFilter
import de.kempmobil.ktor.mqtt.ws.MqttClient
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.dom.appendElement
import kotlinx.dom.appendText
import org.w3c.dom.Element
import org.w3c.dom.HTMLButtonElement
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val url = Url("wss://test.mosquitto.org:8091")
private val mainScope = CoroutineScope(Dispatchers.Default)
private val clock = Clock.System
private var packetCount = 0L
private var payloadCount = 0L
private var connected = false
private var started: Instant? = null

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
        val statusNode = appendElement("div") {
            appendText("")
        }
        val timeNode = appendElement("div") {
            appendText("Running since: -")
        }
        val payloadNode = appendElement("div") {
            appendText("Payload received [bytes]: 0")
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
                packetCount = 0L
                payloadCount = 0L
                mainScope.launch {
                    val connectResult = client.connect()
                    if (connectResult.isSuccess) {
                        client.subscribe(listOf(TopicFilter(Topic("#"))))
                    } else {
                        connectResult.exceptionOrNull()?.printStackTrace()
                        statusNode.textContent = "Connection failed: ${connectResult.exceptionOrNull()?.message}"
                    }
                }
            }
        }

        mainScope.launch {
            client.connectionState.collect {
                if (it.isConnected) {
                    started = clock.now()
                    connected = true
                    statusNode.textContent = "Connected"
                    startStop.textContent = "Stop"
                } else {
                    started = null
                    connected = false
                    statusNode.textContent = "Not connected"
                    startStop.textContent = "Start"
                }
            }
        }
        mainScope.launch {
            client.publishedPackets.collect {
                packetCount++
                payloadCount += it.payload.size
                counterNode.textContent = "Packets received: $packetCount, last topic: ${it.topic.name}"
                payloadNode.textContent = "Payload received [bytes]: $payloadCount"
            }
        }
        mainScope.launch {
            while (true) {
                started?.let {
                    timeNode.textContent = "Running since: ${clock.now() - it}"
                } ?: run {
                    timeNode.textContent = "Running since: -"
                }
                delay(850)
            }
        }
    }
}