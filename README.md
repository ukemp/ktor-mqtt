# Multiplatform Kotlin MQTT Client Library

A multiplatform Kotlin MQTT 5 client library, which is built on Ktor, `kotlinx-io` and Coroutines. It
provides a fast and easy setup of asynchronous MQTT 5 clients without the need to use callbacks. It
allows connections to MQTT servers via plain sockets or via websockets.

This library does not support MQTT 3.

## Usage

Creating a client, subscribing to a topic and receiving PUBLISH packets is as simple as:

```kotlin
runBlocking {
    val client = MqttClient("test.mosquitto.org", 1884) {
        username = "ro"
        password = "readonly"
    }

    val receiver = launch {
        client.publishedPackets.take(100).collect { publish ->
            println("New publish packet received: $publish")
        }
    }

    client.connect().onSuccess { connack ->
        if (connack.isSuccess) {
            client.subscribe(buildFilterList { +"#" })
        }
    }

    receiver.join()
    client.disconnect()
}
```

### Android

Ktor and this library are based on [`kotlinx-io`](https://github.com/Kotlin/kotlinx-io/), which is
available for Android 5.0+ (API level 21+).