package dev.reforge.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

private const val KEX_CLIENT = "com.offsec.nethunter.kex"
private val KBg = Color(0xFF0B0E14)
private val KPrompt = Color(0xFF43D08A)
private val KOut = Color(0xFFAEB9C9)
private val KCmd = Color(0xFFE6ECF5)

@Composable
fun KexScreen(onBack: () -> Unit, vm: KexViewModel = viewModel()) {
    val ctx = LocalContext.current
    var pw by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.log.size, vm.log.lastOrNull()?.text?.length) {
        if (vm.log.isNotEmpty()) listState.animateScrollToItem(vm.log.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("KeX Desktop", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clear() }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear", tint = MutedDark)
            }
        }

        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // status
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(12.dp).clip(CircleShape).background(if (vm.running == true) Accent else MutedDark))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (vm.running == true) "Running — display ${vm.display}" else "Stopped",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(vm.address, style = MaterialTheme.typography.bodyMedium, color = MutedDark, fontFamily = FontFamily.Monospace)
                    }
                    if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
                }
            }

            // resolution
            Text("Resolution", style = MaterialTheme.typography.labelLarge, color = MutedDark)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.resolutions) { r ->
                    val sel = r == vm.resolution
                    Text(
                        r,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (sel) Color(0xFF0B0E13) else Accent,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .let { if (sel) it.background(Accent) else it.border(1.dp, Accent, RoundedCornerShape(50)) }
                            .clickable(enabled = !vm.busy) { vm.chooseResolution(r) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // password
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = pw, onValueChange = { pw = it },
                    modifier = Modifier.weight(1f), singleLine = true, enabled = !vm.busy,
                    placeholder = { Text(if (vm.passwordSet == true) "password set — change it" else "set a VNC password") },
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark2, unfocusedContainerColor = SurfaceDark2,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Accent,
                    ),
                )
                OutlinedButton(onClick = { if (pw.isNotBlank()) { vm.setPassword(pw); pw = "" } }, enabled = !vm.busy && pw.isNotBlank()) {
                    Text("Set")
                }
            }

            // start/stop + connect
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (vm.running == true) {
                    Button(
                        onClick = { vm.stop() }, enabled = !vm.busy, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2130), contentColor = Color(0xFFE88B8B)),
                    ) { Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp)); Text("  Stop") }
                } else {
                    Button(
                        onClick = { vm.start() }, enabled = !vm.busy && vm.passwordSet == true, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
                    ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp)); Text("  Start") }
                }
                OutlinedButton(
                    onClick = { openKexClient(ctx) },
                    enabled = vm.running == true,
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)); Text("  Connect") }
            }
        }

        // log
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(KBg),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (vm.log.isEmpty()) {
                item {
                    Text(
                        "Set a password, Start, then Connect with the NetHunter KeX client " +
                            "(or any VNC viewer at 127.0.0.1:5901).",
                        color = Color(0xFF5A6677), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    )
                }
            }
            items(vm.log, key = { it.id }) { line -> KLine(line) }
        }
    }
}

private fun openKexClient(ctx: android.content.Context) {
    val pm = ctx.packageManager
    val intent = pm.getLaunchIntentForPackage(KEX_CLIENT)
    if (intent != null) {
        ctx.startActivity(intent)
    } else {
        Toast.makeText(ctx, "NetHunter KeX client not installed — connect any VNC viewer to 127.0.0.1:5901", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun KLine(line: TermLine) {
    when (line.kind) {
        TermKind.COMMAND -> Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("❯ ", color = KPrompt, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(line.text, color = KCmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        TermKind.OUTPUT -> Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(line.text, color = KOut, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
        }
        TermKind.INFO -> Text(line.text, color = Color(0xFF7FA8C9), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
