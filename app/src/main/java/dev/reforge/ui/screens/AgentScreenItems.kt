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

@Composable
internal fun MessageItem(msg: ChatMessage, onDownload: () -> Unit = {}) {
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
internal fun ConfirmCard(command: String, onRun: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2016)).padding(14.dp),
    ) {
        Text(
            "⚠ This command looks destructive",
            style = MaterialTheme.typography.titleSmall, color = Color(0xFFE0C089),
        )
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0C1016))
                .horizontalScroll(rememberScrollState()).padding(10.dp),
        ) {
            Text(command, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFE88B8B))
        }
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
            FilledIconButtonText(onClick = onRun, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun FilledIconButtonText(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE88B8B), contentColor = Color(0xFF0B0E13),
        ),
    ) { Text("Run anyway") }
}

@Composable
internal fun ThinkingRow() {
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

