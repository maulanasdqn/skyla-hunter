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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
fun HidScreen(onBack: () -> Unit, vm: HidViewModel = viewModel()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("DuckHunter (HID)", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (vm.hidReady == true) Accent else MutedDark)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (vm.hidReady == true) "/dev/hidg0 ready" else "HID gadget not found",
                        style = MaterialTheme.typography.bodyMedium, color = MutedDark,
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Presets", style = MaterialTheme.typography.labelLarge, color = MutedDark)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.presets) { p ->
                    Text(
                        p.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, Accent, RoundedCornerShape(50))
                            .clickable(enabled = !vm.busy) { vm.loadPreset(p) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            TextField(
                value = vm.script,
                onValueChange = { vm.edit(it) },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                enabled = !vm.busy,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                placeholder = { Text("STRING hello\nENTER") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark2,
                    unfocusedContainerColor = SurfaceDark2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Accent,
                ),
            )

            Button(
                onClick = { vm.send() },
                enabled = !vm.busy && vm.hidReady == true,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
            ) {
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF0B0E13))
                } else {
                    Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("   Inject keystrokes")
                }
            }

            vm.status?.let { Text(it, color = MutedDark, style = MaterialTheme.typography.bodyMedium) }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2016)),
            ) {
                Text(
                    "⚠ Keystrokes go to whatever computer the phone is plugged into via " +
                        "USB — right now that's your dev machine. Only run payloads against " +
                        "a host you own or are authorized to test.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE0C089),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}
