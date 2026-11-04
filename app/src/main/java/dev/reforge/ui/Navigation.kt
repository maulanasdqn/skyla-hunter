package dev.reforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.MutedDark
import dev.reforge.ui.theme.SurfaceDark

object Routes {
    const val DASHBOARD = "dashboard"
    const val AGENT = "agent"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val SERVICES = "services"
    const val TERMINAL = "terminal"
    const val CHROOT = "chroot"
    const val MAC = "mac"
    const val HID = "hid"
    const val PAYLOAD = "payload"
    const val HYDRA = "hydra"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val leftTabs = listOf(
    Tab(Routes.DASHBOARD, "Home", Icons.Filled.GridView),
    Tab(Routes.TOOLS, "Tools", Icons.Filled.Apps),
)
private val rightTabs = listOf(
    Tab(Routes.TERMINAL, "Terminal", Icons.Filled.Terminal),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun ReforgeBottomBar(current: String, onSelect: (String) -> Unit) {
    Surface(color = SurfaceDark) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(70.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            leftTabs.forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onSelect(it.route) } }
            CenterAgent(current == Routes.AGENT, Modifier.weight(1f)) { onSelect(Routes.AGENT) }
            rightTabs.forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onSelect(it.route) } }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) Accent else MutedDark
    Column(
        modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(4.dp))
        Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CenterAgent(selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Big raised center action — the AI Agent. Bottom padding floats it above
    // the smaller side tabs while staying within the bar's bounds (no clipping).
    Box(
        modifier.padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .shadow(14.dp, CircleShape, spotColor = Accent, ambientColor = Accent)
                .clip(CircleShape)
                .background(Accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AutoAwesome, contentDescription = "Agent",
                tint = Color(0xFF0B0E13),
                modifier = Modifier.size(if (selected) 32.dp else 30.dp),
            )
        }
    }
}
