# Multiplatform Kotlin MQTT Client Library

A multiplatform Kotlin MQTT 5 client library, which is built on Ktor, `kotlinx-io` and Coroutines. It
provides a fast and easy setup of asynchronous MQTT 5 clients without the need to use callbacks. It
allows connections to MQTT servers via plain sockets or via websockets.

This library does not support MQTT 3.

## Subscribing to a topic
Creating a client, subscribing to a topic and receiving PUBLISH packets is as simple as:
```kotlin
runBlocking {
    val client = MqttClient("test.mosquitto.org", 1884) {
        username = "ro"
        password = "readonly"
    }

    // Monitor the state of the connection
    launch {
        client.connectionState.fold(Disconnected as ConnectionState) { previous, current ->
            if (current is Disconnected && previous is Connected) {
                cancel()
            }
            println("Client is${if (current is Disconnected) " not " else " "}connected")
            current
        }
    }

    // Print the first 100 published packets
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

## Using in your project

Make sure that you have `mavenCentral()` in the list of repositories:

```kotlin
repositories {
    mavenCentral()
}
```

Add the library to dependencies:

```kotlin
dependencies {
    implementation("de.kempmobil.ktor.mqtt:mqtt-core:0.5.0")
    implementation("de.kempmobil.ktor.mqtt:mqtt-client:0.5.0")
}
```

In multiplatform projects, add a dependency to the `commonMain` source set dependencies:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("de.kempmobil.ktor.mqtt:mqtt-core:0.5.0")
                implementation("de.kempmobil.ktor.mqtt:mqtt-client:0.5.0")
            }
        }
    }
}
```

### Android

Ktor and this library are based on [`kotlinx-io`](https://github.com/Kotlin/kotlinx-io/), which is
available for Android 5.0+ (API level 21+).