package de.kempmobil.ktor.mqtt.app

import androidx.compose.runtime.State
import androidx.compose.runtime.asIntState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Severity
import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.buildFilterList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MqttViewModel : ViewModel() {

    private val _server = mutableStateOf("test.mosquitto.org")
    val server: State<String> = _server

    private val _port = mutableStateOf(1884)
    val port: State<Int> = _port

    private val _user = mutableStateOf("ro")
    val user: State<String> = _user

    private val _password = mutableStateOf("readonly")
    val password: State<String> = _password

    private val _useTls = mutableStateOf(false)
    val useTls: State<Boolean> = _useTls

    private val _topic = mutableStateOf("#")
    val topic: State<String> = _topic

    private val _error = mutableStateOf("")
    val error: State<String> = _error

    private val _packetsReceived = mutableStateOf(0)
    val packetsReceived: State<Int> = _packetsReceived.asIntState()

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    private var client: MqttClient? = null

    private var receiverJob: Job? = null

    private var isDirty = false

    fun server(name: String) {
        _server.value = name
        isDirty = true
    }

    fun port(port: String) {
        _port.value = port.toIntOrNull() ?: 1884
        isDirty = true
    }

    fun username(name: String) {
        _user.value = name
        isDirty = true
    }

    fun password(pwd: String) {
        _password.value = pwd
        isDirty = true
    }

    fun useTls(use: Boolean) {
        _useTls.value = use
        isDirty = true
    }

    fun topic(name: String) {
        _topic.value = name
        isDirty = true
    }

    fun startStop() {
        if (_isConnected.value) {
            viewModelScope.launch {
                client!!.disconnect()
                println("Client is disconnected now")
            }
        } else {
            _packetsReceived.value = 0
            _error.value = ""
            viewModelScope.launch {
                startServer()
            }
        }
    }

    suspend fun startServer() {
        client().connect().onSuccess { connack ->
            if (connack.isSuccess && topic.value.isNotEmpty()) {
                client!!.subscribe(buildFilterList { +topic.value })
            }
        }.onFailure {
            println("An error occurred while trying to connect to ${server.value}")
            it.printStackTrace()
            _error.value = it.message ?: it::class.simpleName!!
        }
    }

    private fun client(): MqttClient {
        if (isDirty || client == null) {
            client?.close()
            receiverJob?.cancel()

            client = MqttClient(server.value, port.value) {
                username = this@MqttViewModel.user.value
                password = this@MqttViewModel.password.value

                logging {
                    minSeverity = Severity.Debug
                }

                if (useTls.value) {
                    connection {
                        tls { }
                    }
                }
            }.also { client ->
                viewModelScope.launch {
                    client.connectionState.collect {
                        _isConnected.value = it.isConnected
                    }
                }

                receiverJob = viewModelScope.launch {
                    client.publishedPackets.collect { publish ->
                        _packetsReceived.value = _packetsReceived.value + 1
                    }
                }
            }
        }
        return client!!
    }
}