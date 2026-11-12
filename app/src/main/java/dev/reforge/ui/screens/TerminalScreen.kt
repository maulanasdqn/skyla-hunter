package dev.reforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark

private val TermBg = Color(0xFF0B0E14)
private val TermBar = Color(0xFF151A23)
private val Prompt = Color(0xFF43D08A)
private val OutText = Color(0xFFAEB9C9)
private val CmdText = Color(0xFFE6ECF5)
private val Dim = Color(0xFF5A6677)

@Composable
fun TerminalScreen(onBack: () -> Unit, vm: TerminalViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
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
            Text("Terminal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clear() }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear", tint = MutedDark)
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E2531), RoundedCornerShape(12.dp))
                .background(TermBg),
        ) {
            TitleBar()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(vm.lines, key = { it.id }) { line -> TermLineView(line) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                placeholder = { Text("command…", fontFamily = FontFamily.Monospace, color = Dim) },
                prefix = { Text("❯ ", color = Prompt, fontFamily = FontFamily.Monospace) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = CmdText),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = { if (input.isNotBlank()) { vm.run(input.trim()); input = "" } }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TermBar,
                    unfocusedContainerColor = TermBar,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Prompt,
                ),
            )
            if (vm.busy) {
                FilledIconButton(
                    onClick = { vm.stop() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF3A2130), contentColor = Color(0xFFE88B8B),
                    ),
                ) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
            } else {
                FilledIconButton(
                    onClick = { if (input.isNotBlank()) { vm.run(input.trim()); input = "" } },
                    enabled = input.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Accent, contentColor = Color(0xFF0B0E13),
                    ),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run") }
            }
        }
    }
}

@Composable
private fun TitleBar() {
    Row(
        Modifier.fillMaxWidth().background(TermBar).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(Color(0xFFFF5F57)); Spacer(Modifier.size(7.dp))
        Dot(Color(0xFFFEBC2E)); Spacer(Modifier.size(7.dp))
        Dot(Color(0xFF28C840))
        Spacer(Modifier.weight(1f))
        Text("root@kali: ~", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Dim)
        Spacer(Modifier.weight(1f))

        Spacer(Modifier.size(53.dp))
    }
}

@Composable
private fun Dot(color: Color) {
    Spacer(Modifier.size(11.dp).clip(CircleShape).background(color))
}

@Composable
private fun TermLineView(line: TermLine) {
    when (line.kind) {
        TermKind.COMMAND -> Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("❯ ", color = Prompt, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Text(line.text, color = CmdText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
        TermKind.OUTPUT -> Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(line.text, color = OutText, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        TermKind.INFO -> Text(
            line.text, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
        )
    }
}
