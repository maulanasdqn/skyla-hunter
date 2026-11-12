package dev.reforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark

@Composable
fun ChrootScreen(onBack: () -> Unit, vm: ChrootViewModel = viewModel()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Chroot", style = MaterialTheme.typography.titleLarge)
                StatusLine(vm.reachable)
            }
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MutedDark)
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (vm.loading && vm.rows.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Accent)
                        }
                    } else {
                        vm.rows.forEach { InfoRowView(it) }
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Chroot size", style = MaterialTheme.typography.titleMedium)
                        Text(
                            vm.size ?: "not measured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (vm.size == null) MutedDark else Accent,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (vm.measuring) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
                    } else {
                        OutlinedButton(onClick = { vm.measureSize() }) { Text("Measure") }
                    }
                }
            }

            Text(
                "The Kali chroot is always mounted and entered on demand — there is no " +
                    "daemon to start or stop. Use Kali Services to control ssh / apache / etc.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedDark,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(reachable: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val color = when (reachable) {
            true -> Accent
            false -> MutedDark
            null -> MutedDark
        }
        Spacer(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(6.dp))
        Text(
            when (reachable) {
                true -> "online"
                false -> "unreachable"
                null -> "checking…"
            },
            style = MaterialTheme.typography.bodyMedium, color = MutedDark,
        )
    }
}

@Composable
private fun InfoRowView(row: InfoRow) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = MaterialTheme.typography.bodyMedium, color = MutedDark, modifier = Modifier.weight(0.4f))
        Text(
            row.value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.6f),
        )
    }
}
