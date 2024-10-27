package de.kempmobil.ktor.mqtt

import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@Ignore
class MosquittoTest {

    private val mosquitto = "test.mosquitto.org"

    private lateinit var client: MqttClient

    @AfterTest
    fun tearDown() = runTest {
        client.disconnect()
    }

    @Test
    fun `test unencrypted websocket connection`() = runTest {
        client = MqttClient(Url("http://$mosquitto:8080")) { }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun `test encrypted websocket connection`() = runTest {
        client = MqttClient(Url("https://$mosquitto:8081")) { }
        val result = client.connect()
        assertEquals(true, result.isSuccess)
    }
}