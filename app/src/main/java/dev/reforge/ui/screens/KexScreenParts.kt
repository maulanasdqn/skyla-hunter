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

internal fun openKexClient(ctx: android.content.Context) {
    val pm = ctx.packageManager
    val intent = pm.getLaunchIntentForPackage(KEX_CLIENT)
    if (intent != null) {
        ctx.startActivity(intent)
    } else {
        Toast.makeText(ctx, "NetHunter KeX client not installed — connect any VNC viewer to 127.0.0.1:5901", Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun KLine(line: TermLine) {
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
