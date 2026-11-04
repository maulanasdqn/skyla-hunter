package dev.reforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.reforge.ui.ToolDef
import dev.reforge.ui.reforgeTools
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

@Composable
fun ToolsScreen(onOpen: (String) -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tools", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(reforgeTools) { g -> ToolRow(g, onOpen) }
        }
    }
}

@Composable
private fun ToolRow(g: ToolDef, onOpen: (String) -> Unit) {
    val enabled = g.route != null
    Card(
        Modifier.fillMaxWidth().let { if (enabled) it.clickable { onOpen(g.route!!) } else it },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (enabled) Accent.copy(alpha = 0.16f) else SurfaceDark2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    g.icon, contentDescription = null,
                    tint = if (enabled) Accent else MutedDark,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium)
                Text(g.detail, style = MaterialTheme.typography.bodyMedium, color = MutedDark)
            }
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null, tint = MutedDark,
                )
            }
        }
    }
}
