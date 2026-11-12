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
internal fun Header(title: String, contextTokens: Int, onNew: () -> Unit, onHistory: () -> Unit) {
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

internal fun fmtTokens(t: Int): String =
    if (t < 1000) "~${t}" else "~%.1fk".format(t / 1000.0)

@Composable
internal fun HistorySheet(
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

internal fun relTime(ts: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        ts, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()

