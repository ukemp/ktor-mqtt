package de.kempmobil.ktor.mqtt.app

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App(model: MqttViewModel = viewModel { MqttViewModel() }) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isEdit = remember { mutableStateOf(false) }
            Box {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Server", style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${model.server.value}:${model.port.value}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            IconButton(onClick = { isEdit.value = !isEdit.value }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit"
                                )
                            }
                        }
                    }
                    Button(onClick = { model.startStop() }) {
                        Text(if (model.isConnected.value) "Stop" else "Start")
                    }

                    Text("State: ${if (model.isConnected.value) "Connected" else "Disconnected"}")
                    Text("Packets received: ${model.packetsReceived.value}")

                    if (model.error.value.isNotEmpty()) {
                        Text(model.error.value)
                    }
                }

                androidx.compose.animation.AnimatedVisibility(isEdit.value, enter = fadeIn(), exit = fadeOut()) {
                    EditServer(model = model, isEnabled = isEdit)
                }
            }
        }
    }
}