package de.kempmobil.ktor.mqtt.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MQTT App",
    ) {
        App()
    }
}