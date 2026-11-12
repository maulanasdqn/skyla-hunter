package dev.reforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.core.IrCode
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

@Composable
fun IrScreen(onBack: () -> Unit, vm: IrViewModel = viewModel()) {
    var addr by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("IR Remote", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SettingsRemote, contentDescription = null, tint = if (vm.hasEmitter) Accent else MutedDark)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (vm.hasEmitter) "IR blaster ready" else "No IR emitter", style = MaterialTheme.typography.titleMedium)
                        Text("Transmit only — point the top of the phone at the device", style = MaterialTheme.typography.bodySmall, color = MutedDark)
                    }
                }
            }

            if (vm.hasEmitter) {

                Button(
                    onClick = { vm.sweep() },
                    enabled = !vm.sweeping,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(if (vm.sweeping) "  sweeping…" else "  TV-OFF sweep (all brands)")
                }

                Text("TV power", style = MaterialTheme.typography.labelLarge, color = MutedDark)
                GridButtons(vm.tvPower) { vm.send(it) }

                Text("Samsung controls", style = MaterialTheme.typography.labelLarge, color = MutedDark)
                GridButtons(vm.controls) { vm.send(it) }

                Text("Custom NEC (hex)", style = MaterialTheme.typography.labelLarge, color = MutedDark)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HexField(addr, { addr = it }, "addr", Modifier.weight(1f))
                    HexField(cmd, { cmd = it }, "cmd", Modifier.weight(1f))
                    Button(
                        onClick = { vm.sendCustomNec(addr, cmd) },
                        enabled = addr.isNotBlank() && cmd.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
                    ) { Text("Send") }
                }

                vm.lastSent?.let { Text(it, color = MutedDark, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace) }

                Text(
                    "Codes are a starter multi-brand set — test against your own device. " +
                        "Only control equipment you own or are permitted to.",
                    style = MaterialTheme.typography.bodySmall, color = MutedDark,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GridButtons(codes: List<IrCode>, onClick: (IrCode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        codes.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { code ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SurfaceDark)
                            .clickable { onClick(code) }.padding(vertical = 14.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(code.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HexField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    TextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isLetterOrDigit() }.take(4)) },
        modifier = modifier, singleLine = true,
        placeholder = { Text(hint) },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceDark2, unfocusedContainerColor = SurfaceDark2,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent,
        ),
    )
}
