package de.kempmobil.ktor.mqtt

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.io.bytestring.decodeToString
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

expect fun createClient(
    id: String,
    configurator: MqttClientConfigBuilder<MqttEngineConfig>.() -> Unit = { }
): MqttClient?

class IntegrationTest {

    private val TIMEOUT = 60.seconds

    @Test
    fun `reconnect after disconnect returns proper connection states`() = runClientTest("reconnect") { client ->
        val asserter = launch {
            val states = client.connectionState.take(5)
                .map {
                    when (it) {
                        is Connected -> 1
                        is Disconnected -> 0
                    }
                }
                .toList()
            assertEquals(listOf(0, 1, 0, 1, 0), states, "Connection states don't match expected sequence")
        }

        yield()
        client.assertConnected()
        yield()
        client.disconnect()
        yield()
        client.assertConnected()
        yield()
        client.disconnect()
        yield()

        asserter.join()
    }

    @Test
    fun `publish and receive messages with QoS 0`() {
        runClientTest(clientId1 = "sender0", clientId2 = "receiver0") { sender: MqttClient, receiver: MqttClient ->
            publishReceiveTest(QoS.AT_MOST_ONCE, sender, receiver)
        }
    }

    @Test
    fun `publish and receive messages with QoS 1`() {
        runClientTest(clientId1 = "sender1", configurator1 = {
            ackMessageTimeout = 1.minutes
        }, clientId2 = "receiver1", configurator2 = {
            ackMessageTimeout = 1.minutes
        }) { sender: MqttClient, receiver: MqttClient ->
            publishReceiveTest(QoS.AT_LEAST_ONCE, sender, receiver)
        }
    }

    @Test
    fun `publish and receive messages with QoS 2`() {
        runClientTest(clientId1 = "sender2", clientId2 = "receiver2") { sender: MqttClient, receiver: MqttClient ->
            publishReceiveTest(QoS.EXACTLY_ONE, sender, receiver)
        }
    }

    @Test
    fun `will message is received when client terminates session without disconnect`() {
        runClientTest(
            clientId1 = "will-sender",
            configurator1 = {
                willMessage("will/topic") {
                    willOqS = QoS.EXACTLY_ONE
                    payload("my-last-will-message")
                }
            },
            clientId2 = "will-receiver"
        ) { sender, receiver ->
            receiver.assertConnected()
            receiver.subscribe(buildFilterList { add("will/topic", qoS = QoS.EXACTLY_ONE) }).onFailure {
                fail("Cannot subscribe to will topic: $it", it)
            }

            val asserter = launch {
                val will = receiver.publishedPackets.take(1).first()
                assertEquals("my-last-will-message", will.payload.decodeToString())
                assertEquals(QoS.EXACTLY_ONE, will.qoS)
            }
            yield()

            sender.assertConnected()
            sender.close() // Do not call disconnect(), as this will prevent will message from being sent
            yield()
            asserter.join()
        }
    }

    // ---- Helper functions -------------------------------------------------------------------------------------------

    private suspend fun TestScope.publishReceiveTest(qoS: QoS, sender: MqttClient, receiver: MqttClient) {
        val topic = "topic/${sender.clientId}/${qoS.value}"
        sender.assertConnected()
        receiver.assertConnected()

        // TODO: once chapter 4.9 "Flow Control" is implemented, we can use any value instead of the receive maximum.
        //   This is only required, because we run each sender in its own scope, if we'd run the sender in a single
        //   scope, all messages are fully acknowledged before sending a new message and hence we can send any number
        //   of messages!
        val messages = receiver.receiveMaximum.toInt().coerceAtMost(100)

        receiver.subscribe(buildFilterList {
            add(topic = topic, qoS = qoS)
        }).onFailure {
            fail("Cannot subscribe to $topic, reason: $it", it)
        }

        val receiverJob = launch {
            receiver.publishedPackets.take(messages).collect { publish ->
                assertTrue(publish.payload.decodeToString().startsWith("message-"))
                assertEquals(qoS, publish.qoS)
                assertEquals("text/plain", publish.contentType?.value)
                assertEquals(PayloadFormatIndicator.UTF_8, publish.payloadFormatIndicator)
            }
        }

        val senders = MutableList(messages) {
            launch {
                val response = sender.publish(PublishRequest(topic) {
                    desiredQoS = qoS
                    contentType = "text/plain"
                    payload("message-$it")
                })
                response.exceptionOrNull()?.printStackTrace()
                assertTrue(response.isSuccess, "Could not publish a message: $response")
            }
        }
        senders.joinAll()
        println("Successfully sent $messages messages")

        // If the received job completes, it will have received exactly the expected number of messages!
        receiverJob.join()
        println("Received $messages messages")

        sender.disconnect()
        receiver.disconnect()
    }

    private suspend fun MqttClient.assertConnected() {
        println("Connecting $clientId...")
        connect()
            .onSuccess { connack ->
                assertTrue(connack.isSuccess, "Unsuccessful CONNACK message received for $clientId: $connack")
                println("$clientId connected")
            }
            .onFailure {
                fail("Cannot connect $clientId to server: ${it.message}", it)
            }
    }

    private fun runClientTest(
        clientId: String,
        timeout: Duration = TIMEOUT,
        configurator: MqttClientConfigBuilder<MqttEngineConfig>.() -> Unit = { },
        test: suspend TestScope.(client: MqttClient) -> Unit
    ) {
        val client = createClient("$clientId-${Random.nextUInt()}", configurator)

        if (client != null) {
            runTest(timeout = timeout) {
                client.use {
                    test(it)
                }
            }
        }
    }

    private fun runClientTest(
        clientId1: String,
        configurator1: MqttClientConfigBuilder<MqttEngineConfig>.() -> Unit = { },
        clientId2: String,
        configurator2: MqttClientConfigBuilder<MqttEngineConfig>.() -> Unit = { },
        timeout: Duration = TIMEOUT,
        test: suspend TestScope.(client1: MqttClient, client2: MqttClient) -> Unit
    ) {
        val client1 = createClient("$clientId1-${Random.nextUInt()}", configurator1)
        val client2 = createClient("$clientId2-${Random.nextUInt()}", configurator2)

        if ((client1 != null) && (client2 != null)) {
            runTest(timeout = timeout) {
                try {
                    test(client1, client2)
                } finally {
                    client1.close()
                    client2.close()
                }
            }
        }
    }
}