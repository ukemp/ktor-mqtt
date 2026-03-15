package de.kempmobil.ktor.mqtt.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val state = rememberWindowState(
        size = DpSize(480.dp, 360.dp),
        position = WindowPosition(300.dp, 300.dp)
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "MQTT App",
        state = state,
    ) {
        App()
    }
}