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
import androidx.compose.material.icons.filled.Key
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

private val services = listOf("ssh", "ftp", "mysql", "postgres", "telnet", "rdp", "smb", "vnc")
private const val DEFAULT_WORDLIST = "/usr/share/wordlists/fasttrack.txt"

private val HBg = Color(0xFF0B0E14)
private val HPrompt = Color(0xFF43D08A)
private val HOut = Color(0xFFAEB9C9)
private val HCmd = Color(0xFFE6ECF5)

@Composable
fun HydraScreen(onBack: () -> Unit, vm: RunnerViewModel = viewModel()) {
    var service by remember { mutableStateOf("ssh") }
    var target by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("root") }
    var wordlist by remember { mutableStateOf(DEFAULT_WORDLIST) }
    // Command built by the Rust core (skyla_core) so preview and run can't drift.
    val preview = uniffi.skyla_core.hydraCmd(user, wordlist, service, target)
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
            Text("Brute-force", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clear() }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear", tint = MutedDark)
            }
        }

        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(services) { s ->
                    val sel = s == service
                    Text(
                        s,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (sel) Color(0xFF0B0E13) else Accent,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .let { if (sel) it.background(Accent) else it.border(1.dp, Accent, RoundedCornerShape(50)) }
                            .clickable(enabled = !vm.busy) { service = s }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HField(target, { target = it }, "target host", Modifier.weight(1.4f), vm.busy)
                HField(user, { user = it }, "username", Modifier.weight(1f), vm.busy)
            }
            HField(wordlist, { wordlist = it }, "password wordlist", Modifier.fillMaxWidth(), vm.busy)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(HBg)
                    .horizontalScroll(rememberScrollState()).padding(10.dp),
            ) {
                Text(preview, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = HPrompt)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(HBg),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (vm.lines.isEmpty()) {
                item {
                    Text(
                        "Authorized targets only. -f stops at the first valid login.",
                        color = Color(0xFF5A6677), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    )
                }
            }
            items(vm.lines, key = { it.id }) { line -> HLine(line) }
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
                        vm.run(uniffi.skyla_core.hydraCmd(user.trim(), wordlist.trim(), service, target.trim()))
                    },
                    enabled = target.isNotBlank() && user.isNotBlank() && wordlist.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
                ) { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(20.dp)); Text("  Run hydra") }
            }
        }
    }
}

@Composable
private fun HField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, busy: Boolean) {
    TextField(
        value = value, onValueChange = onChange, modifier = modifier, enabled = !busy, singleLine = true,
        placeholder = { Text(hint, fontSize = 13.sp) },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceDark2, unfocusedContainerColor = SurfaceDark2,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent,
        ),
    )
}

@Composable
private fun HLine(line: TermLine) {
    when (line.kind) {
        TermKind.COMMAND -> Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("❯ ", color = HPrompt, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(line.text, color = HCmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        TermKind.OUTPUT -> Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(line.text, color = HOut, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
        }
        TermKind.INFO -> Text(line.text, color = Color(0xFF5A6677), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
