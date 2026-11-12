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
            if (state.busy && !state.streaming && state.pendingConfirm == null) item { ThinkingRow() }
        }

        state.pendingConfirm?.let { cmd ->
            ConfirmCard(
                command = cmd,
                onRun = { vm.resolveConfirm(true) },
                onSkip = { vm.resolveConfirm(false) },
            )
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
