package dev.reforge.ui.screens

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KexViewModel : ViewModel() {
    val log = mutableStateListOf<TermLine>()
    var running by mutableStateOf<Boolean?>(null)
        private set
    var passwordSet by mutableStateOf<Boolean?>(null)
        private set
    var resolution by mutableStateOf("1280x720")
        private set
    var busy by mutableStateOf(false)
        private set

    val resolutions = listOf("1280x720", "1600x900", "1920x1080")
    val display = ":1"
    val address = "127.0.0.1:5901"

    private var seq = 0L
    private var outId: Long? = null

    init { refresh() }

    fun chooseResolution(r: String) { if (!busy) resolution = r }

    fun refresh() {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                val run = RootShell.chroot(
                    "pgrep -f 'Xtigervnc.*:1' >/dev/null 2>&1 && echo RUN || echo STOP"
                ).contains("RUN")
                val pass = RootShell.chroot(
                    "[ -f /root/.vnc/passwd ] && echo YES || echo NO"
                ).contains("YES")
                run to pass
            }
            running = res.first
            passwordSet = res.second
        }
    }

    fun setPassword(pw: String) {
        if (busy) return
        if (pw.length < 6) { addInfo("Password must be at least 6 characters (VNC uses the first 8)."); return }
        busy = true
        viewModelScope.launch {
            val b64 = Base64.encodeToString(pw.toByteArray(), Base64.NO_WRAP)
            val out = withContext(Dispatchers.IO) {
                RootShell.chroot(
                    "mkdir -p /root/.vnc && echo $b64 | base64 -d | vncpasswd -f > /root/.vnc/passwd && " +
                        "chmod 600 /root/.vnc/passwd && echo PASS_OK"
                )
            }
            val ok = out.contains("PASS_OK")
            passwordSet = ok
            addInfo(if (ok) "VNC password set." else "Failed to set password: ${out.trim().take(120)}")
            busy = false
        }
    }

    fun start() {
        if (busy) return
        if (passwordSet != true) { addInfo("Set a VNC password first."); return }
        busy = true
        addCmd("vncserver :1 -geometry $resolution")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val cmd = "export USER=root LOGNAME=root; " +
                    "vncserver -kill :1 >/dev/null 2>&1; " +
                    "rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 2>/dev/null; " +
                    "vncserver :1 -geometry $resolution -depth 24 -localhost yes 2>&1"
                RootShell.chrootStream(cmd, timeoutSec = 120) { chunk ->
                    viewModelScope.launch(Dispatchers.Main) { appendOut(chunk) }
                }
            }
            busy = false
            refresh()
        }
    }

    fun stop() {
        if (busy) return
        busy = true
        addCmd("vncserver -kill :1")
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                RootShell.chroot("export USER=root LOGNAME=root; vncserver -kill :1 2>&1")
            }
            appendOut(out)
            busy = false
            refresh()
        }
    }

    fun clear() { log.clear(); outId = null }

    private fun addCmd(s: String) { log.add(TermLine(seq++, TermKind.COMMAND, s)); outId = null }
    private fun addInfo(s: String) { log.add(TermLine(seq++, TermKind.INFO, s)); outId = null }
    private fun appendOut(chunk: String) {
        val id = outId
        if (id == null) {
            val l = TermLine(seq++, TermKind.OUTPUT, chunk.trimEnd('\n'))
            log.add(l); outId = l.id
        } else {
            val idx = log.indexOfLast { it.id == id }
            if (idx >= 0) log[idx] = log[idx].copy(text = (log[idx].text + chunk).trimEnd('\n'))
        }
    }
}
