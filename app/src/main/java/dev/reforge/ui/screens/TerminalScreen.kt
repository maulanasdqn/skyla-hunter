package dev.reforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark2

@Composable
fun TerminalScreen(onBack: () -> Unit, vm: TerminalViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(vm.output) { scroll.animateScrollTo(scroll.maxValue) }

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
            Modifier.weight(1f).fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(Color(0xFF0C1016), RoundedCornerShape(10.dp))
                .verticalScroll(scroll)
                .padding(12.dp),
        ) {
            Text(
                vm.output,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFCBD3E1),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("command…") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = { if (input.isNotBlank()) { vm.run(input.trim()); input = "" } }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark2,
                    unfocusedContainerColor = SurfaceDark2,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Accent,
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
