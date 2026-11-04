package dev.reforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark

private data class ToolGroup(val name: String, val detail: String)

private val groups = listOf(
    ToolGroup("Recon / Scan", "rustscan, nmap, masscan, whatweb"),
    ToolGroup("OSINT", "recon-ng, spiderfoot, amass, theHarvester, sherlock"),
    ToolGroup("Web", "nikto, ffuf, feroxbuster, sqlmap, wpscan"),
    ToolGroup("Passwords", "hydra, john, hashcat, medusa"),
    ToolGroup("Chroot & Services", "Kali manager, services, terminal"),
    ToolGroup("USB / HID", "BadUSB, DuckHunter, USB Arsenal"),
)

@Composable
fun ToolsScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tools", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups) { g ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium)
                        Text(g.detail, style = MaterialTheme.typography.bodyMedium, color = MutedDark)
                    }
                }
            }
        }
    }
}
