package dev.reforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.reforge.core.Settings
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    var key by remember { mutableStateOf(settings.deepSeekKey) }
    var model by remember { mutableStateOf(settings.model) }
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var headers by remember { mutableStateOf(settings.customHeaders) }
    var extraBody by remember { mutableStateOf(settings.extraBody) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI Agent", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Key and prompts are stored on-device and sent only to the endpoint " +
                        "you set below. Any OpenAI-compatible API works; default is DeepSeek.",
                    style = MaterialTheme.typography.bodyMedium, color = MutedDark,
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it; saved = false },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it; saved = false },
                    label = { Text("Base URL") },
                    placeholder = { Text(Settings.DEFAULT_BASE) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model, onValueChange = { model = it; saved = false },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "\"/chat/completions\" is appended to the Base URL automatically.",
                    style = MaterialTheme.typography.labelSmall, color = MutedDark,
                )
                OutlinedTextField(
                    value = headers, onValueChange = { headers = it; saved = false },
                    label = { Text("Custom headers (optional)") },
                    placeholder = { Text("X-My-Header: value\nX-Another: value") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = extraBody, onValueChange = { extraBody = it; saved = false },
                    label = { Text("Extra body JSON (optional)") },
                    placeholder = { Text("{\"uncensored\": true}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "One \"Key: Value\" per line for headers. Extra JSON is merged into " +
                        "the request body — use only what your provider documents.",
                    style = MaterialTheme.typography.labelSmall, color = MutedDark,
                )
                Button(onClick = {
                    settings.deepSeekKey = key.trim()
                    settings.model = model.trim().ifBlank { "deepseek-chat" }
                    settings.baseUrl = baseUrl.trim().ifBlank { Settings.DEFAULT_BASE }
                    settings.customHeaders = headers.trim()
                    settings.extraBody = extraBody.trim()
                    baseUrl = settings.baseUrl
                    saved = true
                }) { Text(if (saved) "Saved" else "Save") }
            }
        }

        Text(
            "Reforge v0.1.0 — a modern front-end for NetHunter.",
            style = MaterialTheme.typography.labelSmall, color = MutedDark,
        )
    }
}
