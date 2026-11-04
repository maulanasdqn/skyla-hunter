package dev.reforge.core

import android.os.Handler
import android.os.Looper
import dev.reforge.ui.Routes

/**
 * The agent's "second hand": lets the DeepSeek agent drive the Reforge app
 * itself (navigate screens, show a toast, read live status) in addition to
 * running commands in the chroot. The UI layer wires the hooks at startup.
 */
object AppController {
    @Volatile var navigate: ((String) -> Unit)? = null
    @Volatile var toast: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    fun handle(action: String, target: String, message: String): String { return when (action) {
        "navigate" -> {
            val route = mapTarget(target) ?: return "unknown target: $target"
            main.post { navigate?.invoke(route) }
            "navigated to $target"
        }
        "toast" -> {
            if (message.isBlank()) return "toast needs a message"
            main.post { toast?.invoke(message) }
            "showed toast: $message"
        }
        "get_status" -> {
            val root = RootShell.hasRoot()
            val os = if (root)
                RootShell.chroot("grep -m1 PRETTY_NAME /etc/os-release | cut -d'\"' -f2").trim()
            else "unavailable"
            "root=$root, chroot=$os"
        }
        else -> "unknown action: $action"
    } }

    private fun mapTarget(t: String): String? = when (t.lowercase().trim()) {
        "home", "dashboard" -> Routes.DASHBOARD
        "agent" -> Routes.AGENT
        "tools" -> Routes.TOOLS
        "settings" -> Routes.SETTINGS
        else -> null
    }
}
