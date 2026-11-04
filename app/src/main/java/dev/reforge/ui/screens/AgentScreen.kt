package dev.reforge.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.ui.MarkdownText
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark
import dev.reforge.ui.theme.SurfaceDark2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(vm: AgentViewModel = viewModel()) {
    val state by vm.state
    var input by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val ctx = LocalContext.current
    val onDownload = {
        val path = ReportExporter.save(ctx, state.title, state.messages)
        Toast.makeText(
            ctx,
            if (path != null) "Report saved to $path" else "Export failed",
            Toast.LENGTH_LONG,
        ).show()
    }

    LaunchedEffect(state.messages.size, state.busy) {
        val count = state.messages.size + if (state.busy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Header(
            title = if (state.title == "New chat") "Recon Agent" else state.title,
            contextTokens = state.contextTokens,
            onNew = { vm.newChat() },
            onHistory = { showHistory = true },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                Box(Modifier.fillMaxWidth().animateItem()) { MessageItem(msg, onDownload) }
            }
            if (state.busy && !state.streaming) item { ThinkingRow() }
        }

        InputBar(
            value = input,
            busy = state.busy,
            onValueChange = { input = it },
            onSend = { if (input.isNotBlank() && !state.busy) { vm.send(input.trim()); input = "" } },
            onStop = { vm.stop() },
        )
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = SurfaceDark,
        ) {
            HistorySheet(
                sessions = state.sessions,
                onOpen = { vm.openSession(it); showHistory = false },
                onDelete = { vm.deleteSession(it) },
                onNew = { vm.newChat(); showHistory = false },
            )
        }
    }
}

@Composable
private fun Header(title: String, contextTokens: Int, onNew: () -> Unit, onHistory: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                "context ${fmtTokens(contextTokens)} / 16k",
                style = MaterialTheme.typography.labelSmall, color = MutedDark,
            )
        }
        IconButton(onClick = onNew) {
            Icon(Icons.Filled.Add, contentDescription = "New chat", tint = Accent)
        }
        IconButton(onClick = onHistory) {
            Icon(Icons.Filled.History, contentDescription = "History", tint = MutedDark)
        }
    }
}

private fun fmtTokens(t: Int): String =
    if (t < 1000) "~${t}" else "~%.1fk".format(t / 1000.0)

@Composable
private fun HistorySheet(
    sessions: List<SessionMeta>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Conversations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(SurfaceDark2)
                    .clickable { onNew() }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New", color = Accent, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (sessions.isEmpty()) {
            Text("No saved conversations yet.", color = MutedDark, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(sessions, key = { it.id }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(s.id) }.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title.ifBlank { "Chat" }, maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                relTime(s.updatedAt), style = MaterialTheme.typography.labelSmall, color = MutedDark,
                            )
                        }
                        IconButton(onClick = { onDelete(s.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MutedDark)
                        }
                    }
                }
            }
        }
    }
}

private fun relTime(ts: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        ts, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()

@Composable
private fun MessageItem(msg: ChatMessage, onDownload: () -> Unit = {}) {
    when (msg.kind) {
        MsgKind.RUNINFO -> {
            val tools = msg.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark).padding(14.dp),
            ) {
                Column {
                    Text("Run complete", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(6.dp))
                    Text("Tools used", style = MaterialTheme.typography.labelSmall, color = MutedDark)
                    Text(
                        tools.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium, color = Accent,
                    )
                    Spacer(Modifier.size(10.dp))
                    OutlinedButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download report")
                    }
                }
            }
        }

        MsgKind.USER -> Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.align(Alignment.CenterEnd).widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    .background(Accent).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(msg.text, color = Color(0xFF0B0E13), style = MaterialTheme.typography.bodyMedium)
            }
        }

        MsgKind.COMMAND -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0C1016)),
        ) {
            Box(Modifier.width(3.dp).heightIn(min = 20.dp).background(Accent))
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Text(msg.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Accent)
            }
        }

        MsgKind.OUTPUT -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceDark)
                .heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Text(msg.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MutedDark)
            }
        }

        MsgKind.THOUGHT -> MarkdownText(
            msg.text, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), muted = true,
        )

        MsgKind.ANSWER -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceDark).padding(14.dp),
        ) {
            MarkdownText(msg.text, modifier = Modifier.fillMaxWidth())
        }

        MsgKind.ERROR -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x332E1416)).padding(12.dp),
        ) {
            Text(msg.text, color = Color(0xFFE88B8B), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ThinkingRow() {
    val t = rememberInfiniteTransition(label = "thinking")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        repeat(3) { idx ->
            val a by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = idx * 180), RepeatMode.Reverse),
                label = "dot$idx",
            )
            Box(Modifier.padding(end = 6.dp).size(7.dp).alpha(a).clip(RoundedCornerShape(50)).background(Accent))
        }
        Spacer(Modifier.width(4.dp))
        Text("thinking…", color = MutedDark, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InputBar(
    value: String,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
            placeholder = { Text("Message the agent…") },
            maxLines = 5,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark2,
                unfocusedContainerColor = SurfaceDark2,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Accent,
            ),
        )
        if (busy) {
            FilledIconButton(
                onClick = onStop,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF3A2130), contentColor = Color(0xFFE88B8B),
                ),
            ) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Accent, contentColor = Color(0xFF0B0E13),
                ),
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        }
    }
}
