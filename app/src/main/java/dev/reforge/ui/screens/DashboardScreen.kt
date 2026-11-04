package dev.reforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val st by vm.state
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Reforge", style = MaterialTheme.typography.headlineMedium)
        Text(
            "NetHunter, reimagined",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedDark,
        )
        Spacer(Modifier.height(6.dp))

        StatusCard(title = "Kali chroot", value = st.chrootValue, online = st.chrootOnline)
        StatusCard(title = "Root (Magisk)", value = st.rootValue, online = st.rootOnline)
        StatusCard(title = "AI Agent", value = "DeepSeek — set key in Settings", online = false)

        Spacer(Modifier.height(4.dp))
        Text("Quick actions", style = MaterialTheme.typography.titleMedium)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("• Ask the Agent to recon a target", color = MutedDark)
                Text("• Start / stop Kali services", color = MutedDark)
                Text("• Open terminal in the chroot", color = MutedDark)
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, online: Boolean?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MutedDark)
            }
            val dot = when (online) {
                true -> Accent
                false -> MutedDark
                null -> MutedDark
            }
            Spacer(
                Modifier.size(12.dp).clip(CircleShape).background(dot)
            )
        }
    }
}
