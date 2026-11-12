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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.ui.Routes
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

@Composable
fun DashboardScreen(onOpen: (String) -> Unit = {}, vm: DashboardViewModel = viewModel()) {
    val st by vm.state
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reforge", style = MaterialTheme.typography.headlineMedium)
                Text("NetHunter, reimagined", style = MaterialTheme.typography.bodyMedium, color = MutedDark)
            }
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MutedDark)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusChip("Root", if (st.rootOnline == true) "Magisk" else "no", st.rootOnline, Modifier.weight(1f))
            StatusChip("Chroot", shortOs(st.chrootOs), st.chrootOnline, Modifier.weight(1f))
        }

        Card(
            Modifier.fillMaxWidth().clickable { onOpen(Routes.AGENT) },
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFF0B0E13), modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Ask the Agent", style = MaterialTheme.typography.titleMedium)
                    Text("DeepSeek · ${st.agentEndpoint}", style = MaterialTheme.typography.bodySmall, color = MutedDark)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Accent)
            }
        }

        SectionCard("System") {
            InfoRow2("Kernel", st.kernel)
            InfoRow2("Disk", st.disk)
            InfoRow2("Memory", st.mem)
            InfoRow2("Uptime", st.uptime)
            InfoRow2("IP", st.ip)
        }

        Card(
            Modifier.fillMaxWidth().clickable { onOpen(Routes.SERVICES) },
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Services", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("manage", style = MaterialTheme.typography.labelMedium, color = Accent)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp).padding(start = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
                if (st.services.isEmpty()) {
                    Text("—", color = MutedDark, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        st.services.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.size(8.dp).clip(CircleShape).background(if (s.running) Accent else MutedDark))
                                Spacer(Modifier.size(5.dp))
                                Text(s.name, style = MaterialTheme.typography.bodySmall, color = if (s.running) MaterialTheme.colorScheme.onSurface else MutedDark)
                            }
                        }
                    }
                }
            }
        }

        SectionCard("Recent recon") {
            if (st.recents.isEmpty()) {
                Text("No conversations yet — ask the Agent to scan a target.", color = MutedDark, style = MaterialTheme.typography.bodyMedium)
            } else {
                st.recents.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(Routes.AGENT) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            Text(r.time, style = MaterialTheme.typography.labelSmall, color = MutedDark)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MutedDark, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun shortOs(os: String): String = when {
    os.contains("Kali", true) -> "Kali"
    else -> os.take(12)
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow2(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedDark, modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun StatusChip(label: String, value: String, online: Boolean?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.size(10.dp).clip(CircleShape).background(if (online == true) Accent else MutedDark))
            Column {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MutedDark, fontWeight = FontWeight.Normal)
            }
        }
    }
}
