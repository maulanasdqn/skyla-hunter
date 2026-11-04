package dev.reforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.reforge.core.AppController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.reforge.ui.ReforgeBottomBar
import dev.reforge.ui.Routes
import dev.reforge.ui.screens.AgentScreen
import dev.reforge.ui.screens.CategoryScreen
import dev.reforge.ui.screens.ChrootScreen
import dev.reforge.ui.screens.DashboardScreen
import dev.reforge.ui.screens.HidScreen
import dev.reforge.ui.screens.HydraScreen
import dev.reforge.ui.screens.KexScreen
import dev.reforge.ui.screens.MacScreen
import dev.reforge.ui.screens.PayloadScreen
import dev.reforge.ui.screens.WirelessScreen
import dev.reforge.ui.screens.ServicesScreen
import dev.reforge.ui.screens.SettingsScreen
import dev.reforge.ui.screens.TerminalScreen
import dev.reforge.ui.screens.ToolsScreen
import dev.reforge.ui.theme.ReforgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ReforgeApp() }
    }
}

@Composable
fun ReforgeApp() {
    ReforgeTheme {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route ?: Routes.DASHBOARD
        val ctx = LocalContext.current

        // wire the agent's "second hand" to the live UI
        LaunchedEffect(nav) {
            AppController.navigate = { route -> nav.navigateTab(route) }
            AppController.toast = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
        }

        Scaffold(
            bottomBar = { ReforgeBottomBar(current) { nav.navigateTab(it) } }
        ) { pad ->
            NavHost(
                navController = nav,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(pad),
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(onOpen = { route -> nav.navigate(route) })
                }
                composable(Routes.AGENT) { AgentScreen() }
                composable(Routes.TOOLS) {
                    ToolsScreen(onOpen = { route -> nav.navigate(route) })
                }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.SERVICES) {
                    ServicesScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.TERMINAL) {
                    TerminalScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.CHROOT) {
                    ChrootScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.MAC) {
                    MacScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.HID) {
                    HidScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.PAYLOAD) {
                    PayloadScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.HYDRA) {
                    HydraScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.KEX) {
                    KexScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.WIRELESS) {
                    WirelessScreen(onBack = { nav.popBackStack() })
                }
                composable("category/{id}") { entry ->
                    CategoryScreen(
                        categoryId = entry.arguments?.getString("id") ?: "recon",
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
