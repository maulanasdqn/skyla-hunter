package dev.reforge.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One readable fact about the chroot / host. */
data class InfoRow(val label: String, val value: String)

/**
 * Chroot Manager. The NetHunter Kali chroot lives at
 * /data/local/nhsystem/kali-arm64 and is always mounted (it is entered on
 * demand, not a daemon), so this screen is an honest system dashboard:
 * OS / kernel / arch / disk / memory / uptime / live process count, plus an
 * on-demand measurement of the chroot's on-disk size (du is slow, so it is
 * a separate action).
 */
class ChrootViewModel(app: Application) : AndroidViewModel(app) {
    var rows by mutableStateOf<List<InfoRow>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var size by mutableStateOf<String?>(null)
        private set
    var measuring by mutableStateOf(false)
        private set
    var reachable by mutableStateOf<Boolean?>(null)
        private set

    init { refresh() }

    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { gather() }
            rows = list
            reachable = list.isNotEmpty()
            loading = false
        }
    }

    fun measureSize() {
        if (measuring) return
        measuring = true
        size = "measuring…"
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) {
                RootShell.root("du -sh /data/local/nhsystem/kali-arm64 2>/dev/null | cut -f1")
                    .trim().ifBlank { "?" }
            }
            size = s
            measuring = false
        }
    }

    private fun gather(): List<InfoRow> {
        // One round-trip: print KEY=VALUE lines we can parse.
        val script = """
            . /etc/os-release 2>/dev/null
            echo "os=${'$'}PRETTY_NAME"
            echo "kernel=${'$'}(uname -r)"
            echo "arch=${'$'}(uname -m)"
            echo "uptime=${'$'}(uptime -p 2>/dev/null | sed 's/^up //')"
            echo "procs=${'$'}(ps -e 2>/dev/null | tail -n +2 | wc -l)"
            echo "disk=${'$'}(df -h / 2>/dev/null | awk 'NR==2{print ${'$'}3" used / "${'$'}2" ("${'$'}5")"}')"
            echo "mem=${'$'}(free -h 2>/dev/null | awk '/^Mem:/{print ${'$'}3" / "${'$'}2}')"
        """.trimIndent()
        val out = RootShell.chroot(script)
        val map = out.lineSequence()
            .mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 } }
            .associate { it[0].trim() to it[1].trim() }
        if (map["os"].isNullOrBlank() && map["kernel"].isNullOrBlank()) return emptyList()
        return listOf(
            InfoRow("OS", map["os"].orEmpty().ifBlank { "Kali Linux" }),
            InfoRow("Kernel", map["kernel"].orEmpty()),
            InfoRow("Architecture", map["arch"].orEmpty()),
            InfoRow("Uptime", map["uptime"].orEmpty().ifBlank { "—" }),
            InfoRow("Memory", map["mem"].orEmpty().ifBlank { "—" }),
            InfoRow("Disk (data)", map["disk"].orEmpty().ifBlank { "—" }),
            InfoRow("Processes", map["procs"].orEmpty().ifBlank { "—" }),
            InfoRow("Path", "/data/local/nhsystem/kali-arm64"),
        )
    }
}
