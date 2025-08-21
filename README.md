# Multiplatform Kotlin MQTT Client Library

A multiplatform Kotlin MQTT 5 client library, which is built on Ktor, `kotlinx-io` and Coroutines. It
provides a fast and easy setup of asynchronous MQTT 5 clients without the need to use callbacks. It
allows connections to MQTT servers via plain sockets or via websockets.

This library does not support MQTT 3.

### Using the library

Creating a client with username password authentication, subscribing to a topic and receiving
PUBLISH packets is as simple as:

```kotlin
runBlocking {
    val client = MqttClient("test.mosquitto.org", 1884) {
        username = "ro"
        password = "readonly"
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
    }.onFailure {
        throw it
    }

    receiver.join()
    client.disconnect()
}
```

### Publishing to a topic

To publish data, create a `PublishRequest` and specify the topic (or topic alias):

```kotlin
client.publish(PublishRequest("topics/test") {
    desiredQoS = QoS.AT_LEAST_ONCE
    messageExpiryInterval = 12.hours
    payload("This text message expires in 12h")
    userProperties {
        "key-1" to "value-1"
    }
})
```

When the `publish()` method returns successfully, all acknowledgement messages required for the QoS level
used, will be transmitted between the server and the client. Note that the `desiredQoS` might be
automatically downgraded, in case the server sent a lower max. QoS in its CONNACK message. The QoS that
was actually used, can be gathered from the returned `PublishResponse`.

`PublishRequest` is a data class, hence you can reuse it, if you just want to change some properties:

```kotlin
val next = publishRequest.copy(payload = "Another payload".encodeToByteString())
```

### Using TLS

To use TLS, enable it in the connection DSL:

```kotlin
val client = MqttClient("test.mosquitto.org", 8886) {
    connection {
        tls { }
    }
}
```

The `tls` part allows you to configure further TLS settings via Ktor
[TLSConfigBuilder](https://api.ktor.io/ktor-network/ktor-network-tls/io.ktor.network.tls/-t-l-s-config-builder/index.html),
for example for the Java platform, you can use your
own [X509TrustManager](https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/X509TrustManager.html).

### Specifying a last will message

A [will message](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc479576982), its topic, payload and
properties are defined in the constructor DSL, for example:

```kotlin
val client = MqttClient("test.mosquitto.org", 1883) {
    willMessage("topics/last/will") {
        payload("Have been here")
        properties {
            willDelayInterval = 1.minutes
        }
    }
}
```

### Logging

By default, the library does not create any log messages. However logging is based on
[Kermit](https://kermit.touchlab.co/) and can be enabled in the logging part of the constructor
DSL; for example:

```kotlin
val client = MqttClient("test.mosquitto.org", 1883) {
    logging {
        minSeverity = Severity.Debug
    }
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
  implementation("de.kempmobil.ktor.mqtt:mqtt-core:0.6.3")
  implementation("de.kempmobil.ktor.mqtt:mqtt-client:0.6.3")
}
```

In multiplatform projects, add a dependency to the `commonMain` source set dependencies:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
              implementation("de.kempmobil.ktor.mqtt:mqtt-core:0.6.3")
              implementation("de.kempmobil.ktor.mqtt:mqtt-client:0.6.3")
            }
        }
    }
}
```

### Android

Ktor and this library are based on [`kotlinx-io`](https://github.com/Kotlin/kotlinx-io/), which is
available for Android 5.0+ (API level 21+).

## Using Web Sockets

If you want to connect to the MQTT server via web sockets, also add the `mqtt-client-ws` library
and **at least one Ktor Http client library**, for example `CIO`:

```kotlin
dependencies {
  implementation("de.kempmobil.ktor.mqtt:mqtt-client-ws:0.6.3")
  implementation("io.ktor:ktor-client-cio:3.1.3")
}
```

Then pass a URL instead of a server name and a port number to the `MqttClient` factory method:

```kotlin
val client = MqttClient("http://test.mosquitto.org:8080") { }
```

- As protocol, you can use either `http:` or `ws:` for plain connections or `https:` or `wss:`  for
  secure connections.
- Ktor will choose the `HttpClient` [automatically](https://ktor.io/docs/client-engines.html#default)
  depending on the artifacts added in your build script. If you need more control over the `HttpClient`
  used, for example, to specify a http proxy and a custom trust manager overwrite the http client
  builder:

```kotlin
val client = MqttClient("https://test.mosquitto.org:8081") {
    connection {
        http = {
            HttpClient(CIO) {
                install(WebSockets) // Remember to install the WebSockets plugin!
                install(Logging)    // Helpful for debugging http connection problems
                engine {
                    proxy = ProxyBuilder.http("http://my.proxy.com:3128")
                    https {
                        trustManager = ...
                    }
                }
            }
        }
    }
}
```

See the [Ktor documentation](https://ktor.io/docs/client-create-and-configure.html) on how to configure a http client.

## Missing features

What's currently missing:

- Handling of MQTT sessions: the clean start flag is currently always set to `true`
- Handling of `Authentication Method` and `Authentication Data` fields in the `CONNACK` message
- Handling of the `Receive Maximum`, `Retain Available`, `Maximum Packet Size`, `Wildcard Subscription Available`
  `Shared Subscription Available` flags in the `CONNACK` message