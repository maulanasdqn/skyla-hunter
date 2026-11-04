package dev.reforge.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ApInfo(
    val bssid: String,
    val essid: String,
    val channel: String,
    val power: Int,     // dBm (negative; closer to 0 = stronger)
    val enc: String,
)

/**
 * Wireless — drives an external monitor-capable adapter (AR9271 / RT3070 class)
 * exposed as a second wlan interface. Toggles monitor mode with `iw` (never
 * `airmon-ng check kill`, which would drop wlan0 / wireless-ADB) and scans with
 * airodump-ng, parsing its CSV into an AP list.
 */
class WirelessViewModel : ViewModel() {
    var iface by mutableStateOf<String?>(null)
        private set
    var driver by mutableStateOf<String?>(null)
        private set
    var mode by mutableStateOf<String?>(null) // "monitor" | "managed" | null
    var busy by mutableStateOf(false)
        private set
    var scanning by mutableStateOf(false)
        private set
    var aps by mutableStateOf<List<ApInfo>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)

    init { detect() }

    fun detect() {
        viewModelScope.launch {
            val line = withContext(Dispatchers.IO) { RootShell.chroot(DETECT).trim() }
                .lineSequence().firstOrNull { it.isNotBlank() }
            if (line == null) {
                iface = null; driver = null; mode = null
                message = "No external adapter. Plug in an AR9271 / RT3070 USB adapter."
            } else {
                val p = line.split(" ")
                iface = p.getOrNull(0)
                driver = p.getOrNull(1)?.ifBlank { "?" }
                mode = p.getOrNull(2)?.ifBlank { null }
                message = null
            }
        }
    }

    fun toggleMonitor() {
        val i = iface ?: return
        if (busy) return
        busy = true
        val toMonitor = mode != "monitor"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val type = if (toMonitor) "monitor" else "managed"
                val chan = if (toMonitor) "; iw dev $i set channel 6" else ""
                RootShell.chroot(
                    "ip link set $i down; iw dev $i set type $type 2>&1; ip link set $i up$chan"
                )
            }
            detect()
            busy = false
        }
    }

    fun scan(seconds: Int = 12) {
        val i = iface ?: return
        if (scanning || busy) return
        if (mode != "monitor") { message = "Enable monitor mode first."; return }
        scanning = true
        message = null
        viewModelScope.launch {
            val csv = withContext(Dispatchers.IO) {
                RootShell.chroot(
                    "rm -f /tmp/rfscan-*.csv 2>/dev/null; " +
                        "timeout $seconds airodump-ng --band bg -w /tmp/rfscan --output-format csv " +
                        "--write-interval 1 $i >/dev/null 2>&1; " +
                        "cat /tmp/rfscan-01.csv 2>/dev/null; rm -f /tmp/rfscan-*.csv 2>/dev/null"
                )
            }
            aps = parseCsv(csv)
            if (aps.isEmpty()) message = "No APs seen — try a different channel/spot."
            scanning = false
        }
    }

    private fun parseCsv(csv: String): List<ApInfo> {
        val out = ArrayList<ApInfo>()
        for (raw in csv.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("BSSID")) continue
            if (line.startsWith("Station MAC")) break // station section — stop
            val c = line.split(",").map { it.trim() }
            if (c.size < 14) continue
            val bssid = c[0]
            if (!bssid.contains(":")) continue
            val channel = c[3]
            val enc = c[5].ifBlank { "OPN" }
            val power = c[8].toIntOrNull() ?: -100
            val essid = c[13].ifBlank { "<hidden>" }
            out.add(ApInfo(bssid, essid, channel, power, enc))
        }
        return out.sortedByDescending { it.power } // strongest first
    }

    companion object {
        // Prints "<iface> <driver> <mode>" for the first external wlan (not wlan0).
        private const val DETECT = """
            for i in ${'$'}(ls /sys/class/net 2>/dev/null); do
              case "${'$'}i" in
                wlan0|lo|p2p0) continue;;
                wlan*|wlx*)
                  drv=${'$'}(basename ${'$'}(readlink /sys/class/net/${'$'}i/device/driver 2>/dev/null) 2>/dev/null)
                  m=${'$'}(iw dev ${'$'}i info 2>/dev/null | grep -o 'type [a-z]*' | awk '{print ${'$'}2}')
                  echo "${'$'}i ${'$'}drv ${'$'}m"
                  break;;
              esac
            done
        """
    }
}
