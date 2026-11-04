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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.ui.ToolDef
import dev.reforge.ui.reforgeTools
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

@Composable
fun DashboardScreen(onOpen: (String) -> Unit = {}, vm: DashboardViewModel = viewModel()) {
    val st by vm.state
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text("Reforge", style = MaterialTheme.typography.headlineMedium)
        Text("NetHunter, reimagined", style = MaterialTheme.typography.bodyMedium, color = MutedDark)
        Spacer(Modifier.height(4.dp))

        // compact status strip
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusChip("Chroot", st.chrootOnline, Modifier.weight(1f))
            StatusChip("Root", st.rootOnline, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        Text("Tools", style = MaterialTheme.typography.titleMedium)

        // tool list, two per row
        reforgeTools.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { tool ->
                    ToolTile(tool, Modifier.weight(1f)) { tool.route?.let(onOpen) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ToolTile(tool: ToolDef, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val enabled = tool.route != null
    Card(
        modifier
            .height(118.dp)
            .let { if (enabled) it.clickable { onClick() } else it },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) Accent.copy(alpha = 0.16f) else SurfaceDark2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tool.icon, contentDescription = null,
                    tint = if (enabled) Accent else MutedDark,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                tool.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MutedDark,
            )
            Text(
                if (enabled) tool.detail else "soon",
                style = MaterialTheme.typography.bodySmall,
                color = MutedDark,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, online: Boolean?, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (online == true) Accent else MutedDark)
            )
            Column {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (online == true) "online" else "offline",
                    style = MaterialTheme.typography.bodySmall, color = MutedDark,
                )
            }
        }
    }
}
