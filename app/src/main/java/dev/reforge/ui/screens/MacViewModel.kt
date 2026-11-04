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

/**
 * MAC changer. Operates on a kernel interface (wlan0 by default; any external
 * adapter plugged in later shows up here too). Each change brings the link
 * down, runs macchanger, brings it back up, then re-reads — all inside the
 * chroot, which shares the host network namespace.
 */
class MacViewModel(app: Application) : AndroidViewModel(app) {
    var ifaces by mutableStateOf(listOf("wlan0"))
        private set
    var selected by mutableStateOf("wlan0")
        private set
    var current by mutableStateOf<String?>(null)
        private set
    var permanent by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private val macRe = Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")

    init {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { detectIfaces() }
            if (found.isNotEmpty()) {
                ifaces = found
                selected = if ("wlan0" in found) "wlan0" else found.first()
            }
            read()
        }
    }

    fun select(iface: String) {
        if (iface == selected || busy) return
        selected = iface
        read()
    }

    fun refresh() = read()

    fun randomize() = runAction("-r")
    fun restore() = runAction("-p")

    fun setCustom(mac: String) {
        val m = mac.trim()
        if (!macRe.matches(m)) { message = "Invalid MAC — use AA:BB:CC:DD:EE:FF"; return }
        runAction("-m $m")
    }

    /** Interfaces worth touching: wifi / ethernet / usb adapters / monitor ifaces. */
    private fun detectIfaces(): List<String> {
        val raw = RootShell.root("ls /sys/class/net 2>/dev/null")
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { n -> listOf("wlan", "eth", "usb", "mon", "ap0", "wlx").any { n.startsWith(it) } }
            .toList()
    }

    private fun read() {
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                RootShell.chroot(
                    "macchanger -s $selected 2>&1; echo ACTUAL=$(cat /sys/class/net/$selected/address 2>/dev/null)"
                )
            }
            parse(out)
        }
    }

    private fun runAction(opt: String) {
        if (busy) return
        busy = true
        message = null
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                // Change while the link is down, bring it back up, then read the
                // REAL kernel MAC from sysfs — the only honest source of truth.
                val script = "ip link set dev $selected down 2>&1; " +
                    "macchanger $opt $selected 2>&1; " +
                    "ip link set dev $selected up 2>&1; sleep 1; " +
                    "macchanger -s $selected 2>&1; " +
                    "echo ACTUAL=$(cat /sys/class/net/$selected/address 2>/dev/null)"
                RootShell.chroot(script)
            }
            // What macchanger says it applied vs. what the kernel actually holds.
            val applied = out.lineSequence().firstOrNull { it.contains("New MAC", true) }
                ?.let { macRe.find(it)?.value }
            val actual = out.lineSequence().firstOrNull { it.startsWith("ACTUAL=") }
                ?.let { macRe.find(it)?.value }
            parse(out)
            message = when {
                actual == null -> "Failed — ${out.lineSequence().firstOrNull { it.isNotBlank() } ?: "see interface"}"
                applied != null && !actual.equals(applied, true) ->
                    "OS reset $selected to $actual — it manages this interface's MAC. " +
                        "Spoofing is reliable on an external adapter."
                else -> "MAC is now $actual"
            }
            busy = false
        }
    }

    private fun parse(out: String) {
        // Prefer the real kernel MAC (ACTUAL=…) over macchanger's own report.
        val actual = out.lineSequence().firstOrNull { it.startsWith("ACTUAL=") }
            ?.let { macRe.find(it)?.value }
        val cur = out.lineSequence().firstOrNull { it.contains("Current", true) }
        val perm = out.lineSequence().firstOrNull { it.contains("Permanent", true) }
        current = actual ?: cur?.let { macRe.find(it)?.value } ?: current
        permanent = perm?.let { macRe.find(it)?.value } ?: permanent
    }
}
