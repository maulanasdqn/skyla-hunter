package dev.reforge.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.DuckyScript
import dev.reforge.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HidPreset(val name: String, val script: String)

class HidViewModel(app: Application) : AndroidViewModel(app) {
    var script by mutableStateOf(DEMO)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var hidReady by mutableStateOf<Boolean?>(null)
        private set

    val presets = listOf(
        HidPreset("Type text", DEMO),
        HidPreset("Win + R → notepad", "GUI r\nDELAY 500\nSTRING notepad\nENTER"),
        HidPreset("macOS Spotlight", "GUI SPACE\nDELAY 400\nSTRING Notes\nENTER"),
        HidPreset("Select all + copy", "CTRL a\nDELAY 100\nCTRL c"),
    )

    init {
        viewModelScope.launch {
            hidReady = withContext(Dispatchers.IO) {
                RootShell.root("[ -e /dev/hidg0 ] && echo yes").trim().contains("yes")
            }
        }
    }

    fun edit(s: String) { script = s }
    fun loadPreset(p: HidPreset) { script = p.script; status = null }

    fun send() {
        if (busy) return
        val sh = DuckyScript.compile(script)
        if (sh.isBlank()) { status = "Nothing typable in the script"; return }
        busy = true
        status = "injecting…"
        viewModelScope.launch {
            withContext(Dispatchers.IO) { RootShell.chrootStream(sh, timeoutSec = 120) {} }
            busy = false
            status = "Sent to the connected computer"
        }
    }

    companion object {
        const val DEMO = "REM Reforge DuckHunter — types into the plugged-in host\nSTRING Hello from Reforge"
    }
}
