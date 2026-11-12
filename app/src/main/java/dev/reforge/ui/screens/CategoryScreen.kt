package dev.reforge.ui.screens

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
import androidx.compose.material.icons.filled.PlayArrow
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
import dev.reforge.ui.QuickTool
import dev.reforge.ui.categoryById
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

private val TermBg = Color(0xFF0B0E14)
private val Prompt = Color(0xFF43D08A)
private val OutText = Color(0xFFAEB9C9)
private val CmdText = Color(0xFFE6ECF5)
private val Dim = Color(0xFF5A6677)

@Composable
fun CategoryScreen(categoryId: String, onBack: () -> Unit, vm: RunnerViewModel = viewModel()) {
    val category = remember(categoryId) { categoryById(categoryId) }
    if (category == null) { onBack(); return }

    var selected by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf("") }
    val tool = category.tools[selected]
    val resolved = tool.command.replace("{t}", target.trim().ifBlank { "<${tool.targetHint}>" })
    val listState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(vm.lines.size, vm.lines.lastOrNull()?.text?.length) {

        if (vm.lines.isNotEmpty()) {
            listState.scrollToItem(vm.lines.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(category.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.clear() }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear", tint = MutedDark)
            }
        }

        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(category.tools) { t ->
                    val idx = category.tools.indexOf(t)
                    val sel = idx == selected
                    Text(
                        t.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sel) Color(0xFF0B0E13) else Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .let { if (sel) it.background(Accent) else it.border(1.dp, Accent, RoundedCornerShape(50)) }
                            .clickable(enabled = !vm.busy) { selected = idx }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            TextField(
                value = target,
                onValueChange = { target = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tool.targetHint) },
                singleLine = true,
                enabled = !vm.busy,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark2,
                    unfocusedContainerColor = SurfaceDark2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Accent,
                ),
            )
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(TermBg)
                    .horizontalScroll(rememberScrollState()).padding(10.dp),
            ) {
                Text(resolved, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Prompt)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp)).background(TermBg),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (vm.lines.isEmpty()) {
                item { Text("Pick a tool, enter a target, Run.", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            }
            items(vm.lines, key = { it.id }) { line -> RunLine(line) }
        }

        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 14.dp)) {
            if (vm.busy) {
                Button(
                    onClick = { vm.stop() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2130), contentColor = Color(0xFFE88B8B)),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("  Stop")
                }
            } else {
                Button(
                    onClick = { vm.run(tool.command.replace("{t}", target.trim())) },
                    enabled = target.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B0E13)),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("  Run ${tool.name.substringBefore(" —")}")
                }
            }
        }
    }
}

@Composable
private fun RunLine(line: TermLine) {
    when (line.kind) {
        TermKind.COMMAND -> Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("❯ ", color = Prompt, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Text(line.text, color = CmdText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
        TermKind.OUTPUT -> Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(line.text, color = OutText, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
        }
        TermKind.INFO -> Text(line.text, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
