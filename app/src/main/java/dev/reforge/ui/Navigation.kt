package dev.reforge.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.reforge.ui.theme.Accent
import dev.reforge.ui.theme.SurfaceDark

object Routes {
    const val DASHBOARD = "dashboard"
    const val AGENT = "agent"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val SERVICES = "services"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, "Home", Icons.Filled.GridView),
    Tab(Routes.AGENT, "Agent", Icons.Filled.AutoAwesome),
    Tab(Routes.TOOLS, "Tools", Icons.Filled.Terminal),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun ReforgeBottomBar(current: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = SurfaceDark) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = SurfaceDark,
                ),
            )
        }
    }
}
