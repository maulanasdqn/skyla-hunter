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
internal fun ApRow(ap: ApInfo, selected: Boolean, onClick: () -> Unit) {
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
internal fun SignalBars(power: Int) {

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
