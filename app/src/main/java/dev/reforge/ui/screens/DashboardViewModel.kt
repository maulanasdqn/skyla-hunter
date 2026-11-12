package dev.reforge.ui.screens

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.KaliServices
import dev.reforge.core.RootShell
import dev.reforge.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServiceDot(val name: String, val running: Boolean)
data class RecentChat(val id: String, val title: String, val time: String)

data class DashState(
    val rootOnline: Boolean? = null,
    val chrootOnline: Boolean? = null,
    val chrootOs: String = "checking…",
    val kernel: String = "—",
    val disk: String = "—",
    val mem: String = "—",
    val uptime: String = "—",
    val ip: String = "—",
    val agentEndpoint: String = "not set",
    val services: List<ServiceDot> = emptyList(),
    val recents: List<RecentChat> = emptyList(),
    val loading: Boolean = true,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = mutableStateOf(DashState())
    val state: State<DashState> = _state

    private val settings = Settings(app)
    private val store = ChatStore(app)

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        // cheap, local-only bits first
        val endpoint = settings.baseUrl
            .removePrefix("https://").removePrefix("http://").substringBefore("/")
            .ifBlank { "not set" }
        val recents = store.list().take(3).map {
            RecentChat(it.id, it.title.ifBlank { "Chat" }, relTime(it.updatedAt))
        }
        _state.value = _state.value.copy(
            agentEndpoint = if (settings.deepSeekKey.isBlank()) "no API key" else endpoint,
            recents = recents,
        )

        viewModelScope.launch {
            val hasRoot = withContext(Dispatchers.IO) { RootShell.hasRoot() }
            _state.value = _state.value.copy(rootOnline = hasRoot)
            if (!hasRoot) {
                _state.value = _state.value.copy(
                    chrootOnline = false, chrootOs = "needs root", loading = false,
                )
                return@launch
            }
            val sys = withContext(Dispatchers.IO) { gatherSystem() }
            val ip = withContext(Dispatchers.IO) {
                RootShell.root("ip route get 1.1.1.1 2>/dev/null | grep -o 'src [0-9.]*' | awk '{print \$2}'").trim()
            }
            val svcs = withContext(Dispatchers.IO) {
                KaliServices.all.map { ServiceDot(it.name, KaliServices.isRunning(it)) }
            }
            val ok = sys["os"]?.contains("Kali", true) == true
            _state.value = _state.value.copy(
                chrootOnline = ok,
                chrootOs = sys["os"].orEmpty().ifBlank { "not reachable" },
                kernel = sys["kernel"].orEmpty().ifBlank { "—" },
                disk = sys["disk"].orEmpty().ifBlank { "—" },
                mem = sys["mem"].orEmpty().ifBlank { "—" },
                uptime = sys["uptime"].orEmpty().ifBlank { "—" },
                ip = ip.ifBlank { "—" },
                services = svcs,
                loading = false,
            )
        }
    }

    private fun gatherSystem(): Map<String, String> {
        val script = """
            . /etc/os-release 2>/dev/null
            echo "os=${'$'}PRETTY_NAME"
            echo "kernel=${'$'}(uname -r)"
            echo "uptime=${'$'}(uptime -p 2>/dev/null | sed 's/^up //')"
            echo "disk=${'$'}(df -h / 2>/dev/null | awk 'NR==2{print ${'$'}3" / "${'$'}2}')"
            echo "mem=${'$'}(free -h 2>/dev/null | awk '/^Mem:/{print ${'$'}3" / "${'$'}2}')"
        """.trimIndent()
        return uniffi.skyla_core.parseKvLines(RootShell.chroot(script))
    }

    private fun relTime(ts: Long): String =
        android.text.format.DateUtils.getRelativeTimeSpanString(
            ts, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
        ).toString()
}
