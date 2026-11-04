package dev.reforge.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual terminal into the Kali chroot. Runs whatever the user types (their own
 * explicit command) and streams output live via RootShell.chrootStream.
 */
class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    var output by mutableStateOf("Kali chroot — type a command and hit run.\n")
        private set
    var busy by mutableStateOf(false)
        private set

    private var job: Job? = null

    fun run(cmd: String) {
        if (cmd.isBlank() || busy) return
        append("\n$ $cmd\n")
        busy = true
        job = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                RootShell.chrootStream(cmd) { chunk ->
                    viewModelScope.launch(Dispatchers.Main) { append(chunk) }
                }
            }
            busy = false
        }
    }

    fun stop() {
        RootShell.cancelProc()
        job?.cancel()
        busy = false
        append("\n^C\n")
    }

    fun clear() {
        output = ""
    }

    private fun append(s: String) {
        var t = output + s
        if (t.length > 20000) t = "…\n" + t.takeLast(20000)
        output = t
    }
}
