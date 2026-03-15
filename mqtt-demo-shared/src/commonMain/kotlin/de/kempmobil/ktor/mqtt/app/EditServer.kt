package de.kempmobil.ktor.mqtt.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditServer(
    modifier: Modifier = Modifier,
    model: MqttViewModel,
    isEnabled: MutableState<Boolean>
) {
    Surface(modifier = modifier) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = model.server.value,
                onValueChange = { model.server(it) },
                label = { Text("Server") },
                enabled = !model.isConnected.value
            )
            TextField(
                value = model.port.value.toString(),
                onValueChange = { model.port(it) },
                label = { Text("Port") },
                enabled = !model.isConnected.value
            )
            TextField(
                value = model.user.value,
                onValueChange = { model.username(it) },
                label = { Text("Username") },
                enabled = !model.isConnected.value
            )
            TextField(
                value = model.password.value,
                onValueChange = { model.password(it) },
                label = { Text("Password") },
                enabled = !model.isConnected.value
            )
            TextField(
                value = model.topic.value,
                onValueChange = { model.topic(it) },
                label = { Text("Subscribe Topic") },
                enabled = !model.isConnected.value
            )
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = model.useTls.value,
                    onCheckedChange = { model.useTls(it) },
                    enabled = !model.isConnected.value
                )
                Text("Use TLS")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { isEnabled.value = false }) {
                Text("Done")
            }
        }
    }
}