package dev.reforge.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TermKind { COMMAND, OUTPUT, INFO }

data class TermLine(val id: Long, val kind: TermKind, val text: String)

/**
 * Manual terminal into the Kali chroot. Keeps output structured (command vs
 * output vs info) so the UI can style each like a real shell session, and
 * streams live via RootShell.chrootStream.
 */
class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    val lines = mutableStateListOf<TermLine>()
    var busy by mutableStateOf(false)
        private set

    private var seq = 0L
    private var outId: Long? = null
    private var job: Job? = null

    init {
        lines.add(TermLine(seq++, TermKind.INFO, "Kali chroot ready — type a command."))
    }

    fun run(cmd: String) {
        if (cmd.isBlank() || busy) return
        lines.add(TermLine(seq++, TermKind.COMMAND, cmd))
        outId = null
        busy = true
        job = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                RootShell.chrootStream(cmd) { chunk ->
                    viewModelScope.launch(Dispatchers.Main) { appendOut(chunk) }
                }
            }
            outId = null
            busy = false
        }
    }

    fun stop() {
        RootShell.cancelProc()
        job?.cancel()
        busy = false
        outId = null
        lines.add(TermLine(seq++, TermKind.INFO, "^C"))
    }

    fun clear() {
        lines.clear()
        outId = null
    }

    private fun appendOut(chunk: String) {
        val id = outId
        if (id == null) {
            val l = TermLine(seq++, TermKind.OUTPUT, chunk.trimEnd('\n'))
            lines.add(l)
            outId = l.id
        } else {
            val idx = lines.indexOfLast { it.id == id }
            if (idx >= 0) {
                var t = lines[idx].text + chunk
                if (t.length > 20000) t = "…\n" + t.takeLast(20000)
                lines[idx] = lines[idx].copy(text = t.trimEnd('\n'))
            }
        }
    }
}
