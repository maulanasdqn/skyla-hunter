package dev.reforge.ui.screens

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashState(
    val rootValue: String = "checking…",
    val rootOnline: Boolean? = null,
    val chrootValue: String = "checking…",
    val chrootOnline: Boolean? = null,
)

class DashboardViewModel : ViewModel() {
    private val _state = mutableStateOf(DashState())
    val state: State<DashState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val hasRoot = withContext(Dispatchers.IO) { RootShell.hasRoot() }
            _state.value = _state.value.copy(
                rootValue = if (hasRoot) "granted" else "not granted",
                rootOnline = hasRoot,
            )
            if (hasRoot) {
                val os = withContext(Dispatchers.IO) {
                    RootShell.chroot("grep -m1 PRETTY_NAME /etc/os-release | cut -d'\"' -f2")
                }.trim()
                val ok = os.contains("Kali", ignoreCase = true)
                _state.value = _state.value.copy(
                    chrootValue = if (ok) os else "not reachable",
                    chrootOnline = ok,
                )
            } else {
                _state.value = _state.value.copy(
                    chrootValue = "needs root", chrootOnline = false,
                )
            }
        }
    }
}
