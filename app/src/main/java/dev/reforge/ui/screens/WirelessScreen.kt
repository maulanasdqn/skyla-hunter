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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

@Composable
fun WirelessScreen(onBack: () -> Unit, vm: WirelessViewModel = viewModel()) {
    val monitor = vm.mode == "monitor"

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Wireless", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.detect() }, enabled = !vm.busy && !vm.scanning) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MutedDark)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // adapter card
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(10.dp).clip(CircleShape).background(if (vm.iface != null) Accent else MutedDark))
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                vm.iface?.let { "$it · ${vm.driver}" } ?: "No external adapter",
                                style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                vm.iface?.let { "mode: ${vm.mode ?: "—"}" } ?: "Plug in AR9271 / RT3070",
                                style = MaterialTheme.typography.bodyMedium, color = MutedDark,
                            )
                        }
                    }
                    if (vm.iface != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Monitor mode", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (vm.busy) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
                            } else {
                                Switch(
                                    checked = monitor, onCheckedChange = { vm.toggleMonitor() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.4f)),
                                )
                            }
                        }
                    }
                }
            }

            // scan button
            Button(
                onClick = { vm.scan() },
                enabled = vm.iface != null && monitor && !vm.scanning && !vm.busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
            ) {
                if (vm.scanning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF0B0E13))
                    Text("  scanning…")
                } else {
                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("  Scan networks")
                }
            }

            vm.message?.let { Text(it, color = MutedDark, style = MaterialTheme.typography.bodyMedium) }

            // selected-AP actions
            vm.selected?.let { ap ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceDark2)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Target: ${ap.essid}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text("${ap.bssid} · ch ${ap.channel} · ${ap.enc}", style = MaterialTheme.typography.bodySmall, color = MutedDark, fontFamily = FontFamily.Monospace)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { vm.deauth() }, enabled = !vm.working,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2130), contentColor = Color(0xFFE88B8B)),
                            ) { Text("Deauth ×10") }
                            Button(
                                onClick = { vm.capture() }, enabled = !vm.working,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
                            ) { Text("Capture") }
                        }
                        if (vm.working) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                                Text("working…", color = MutedDark, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        vm.action?.let { Text(it, color = MutedDark, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
        }

        // AP list
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.aps, key = { it.bssid }) { ap ->
                ApRow(ap, selected = vm.selected?.bssid == ap.bssid) { vm.select(ap) }
            }
        }

        Text(
            "Monitor/injection affect networks around you — only scan or attack " +
                "targets you own or are authorized to test.",
            style = MaterialTheme.typography.bodySmall, color = MutedDark,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun ApRow(ap: ApInfo, selected: Boolean, onClick: () -> Unit) {
    val open = ap.enc.contains("OPN", true) || ap.enc.isBlank()
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (selected) SurfaceDark2 else SurfaceDark),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SignalBars(ap.power)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(ap.essid, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${ap.bssid}  ·  ch ${ap.channel}",
                    style = MaterialTheme.typography.bodySmall, color = MutedDark, fontFamily = FontFamily.Monospace,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (open) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (open) Color(0xFFE0A05A) else MutedDark,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(ap.enc.ifBlank { "OPN" }, style = MaterialTheme.typography.labelMedium, color = MutedDark)
            }
        }
    }
}

@Composable
private fun SignalBars(power: Int) {
    // -40 strong … -90 weak → 0..4 bars
    val level = when {
        power >= -50 -> 4
        power >= -62 -> 3
        power >= -74 -> 2
        power >= -86 -> 1
        else -> 0
    }
    Row(verticalAlignment = Alignment.Bottom) {
        repeat(4) { idx ->
            Box(
                Modifier.padding(end = 2.dp).width(4.dp).height((5 + idx * 4).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (idx < level) Accent else SurfaceDark2),
            )
        }
    }
}
