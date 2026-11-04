package dev.reforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

@Composable
fun MacScreen(onBack: () -> Unit, vm: MacViewModel = viewModel()) {
    var custom by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("MAC Changer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MutedDark)
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Interface picker
            Text("Interface", style = MaterialTheme.typography.labelLarge, color = MutedDark)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.ifaces) { iface ->
                    val sel = iface == vm.selected
                    Text(
                        iface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (sel) Color(0xFF0B0E13) else Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .let { if (sel) it.background(Accent) else it.border(1.dp, Accent, RoundedCornerShape(50)) }
                            .clickable(enabled = !vm.busy) { vm.select(iface) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Current / permanent
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacLine("Current", vm.current, Accent)
                    MacLine("Permanent", vm.permanent, MutedDark)
                }
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.randomize() },
                    enabled = !vm.busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
                ) {
                    Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.height(1.dp))
                    Text("  Randomize")
                }
                OutlinedButton(
                    onClick = { vm.restore() },
                    enabled = !vm.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.height(18.dp))
                    Text("  Restore")
                }
            }

            // Custom MAC
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = custom,
                    onValueChange = { custom = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    enabled = !vm.busy,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark2,
                        unfocusedContainerColor = SurfaceDark2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Accent,
                    ),
                )
                IconButton(
                    onClick = { if (custom.isNotBlank()) { vm.setCustom(custom); custom = "" } },
                    enabled = !vm.busy && custom.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Set", tint = Accent)
                }
            }

            if (vm.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp, color = Accent)
                    Text("applying — link down/up…", color = MutedDark, style = MaterialTheme.typography.bodyMedium)
                }
            }
            vm.message?.let {
                Text(it, color = MutedDark, style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "Changing a MAC briefly drops the link. wlan0 is the phone's internal " +
                    "radio; an external adapter appears here once plugged in.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedDark,
            )
        }
    }
}

@Composable
private fun MacLine(label: String, mac: String?, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedDark, modifier = Modifier.width(100.dp))
        Text(
            mac ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            color = if (mac == null) MutedDark else valueColor,
        )
    }
}
