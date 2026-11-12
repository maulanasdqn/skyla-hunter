package dev.reforge.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

data class ToolDef(
    val name: String,
    val detail: String,
    val icon: ImageVector,
    val route: String? = null,
)

val reforgeTools = listOf(
    ToolDef("Terminal", "Run commands in the Kali chroot", Icons.Filled.Terminal, Routes.TERMINAL),
    ToolDef("Chroot", "System info, disk usage, status", Icons.Filled.Layers, Routes.CHROOT),
    ToolDef("KeX Desktop", "TigerVNC Kali XFCE desktop (port 5901)", Icons.Filled.DesktopWindows, Routes.KEX),
    ToolDef("Wireless", "Monitor mode + AP scan (external adapter)", Icons.Filled.Wifi, Routes.WIRELESS),
    ToolDef("IR Remote", "TV-B-Gone + universal remote (IR blaster)", Icons.Filled.SettingsRemote, Routes.IR),
    ToolDef("MAC Changer", "Spoof / randomize interface MAC", Icons.Filled.Shuffle, Routes.MAC),
    ToolDef("Kali Services", "ssh, apache, postgresql, mariadb", Icons.Filled.Dns, Routes.SERVICES),
    ToolDef("Recon / Scan", "nmap, rustscan, whatweb, wafw00f", Icons.Filled.Radar, "category/recon"),
    ToolDef("Web", "nikto, ffuf, feroxbuster, sqlmap, wpscan", Icons.Filled.Language, "category/web"),
    ToolDef("OSINT — People", "holehe, maigret, phoneinfoga, exiftool", Icons.Filled.TravelExplore, "category/osint"),
    ToolDef("OSINT — Domain", "theHarvester, subfinder, dnsrecon, whois", Icons.Filled.Dns, "category/osintdom"),
    ToolDef("Exploits", "searchsploit — Exploit-DB search", Icons.Filled.BugReport, "category/exploits"),
    ToolDef("Payloads", "msfvenom payload generator", Icons.Filled.Memory, Routes.PAYLOAD),
    ToolDef("Brute-force", "hydra — ssh/ftp/mysql/rdp/smb login", Icons.Filled.Key, Routes.HYDRA),
    ToolDef("DuckHunter (HID)", "BadUSB keystroke injection via /dev/hidg0", Icons.Filled.Usb, Routes.HID),
)
