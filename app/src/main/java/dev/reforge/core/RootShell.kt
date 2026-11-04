package dev.reforge.core

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs commands as root (Magisk su) and inside the Kali NetHunter chroot.
 * Mirrors the NetHunter layout: chroot at /data/local/nhsystem/kali-arm64.
 *
 * Phase 1 uses this directly from the ViewModel; later the Rust agent core
 * calls back into it through a UniFFI interface so the LLM can execute tools.
 */
object RootShell {
    private const val CHROOT = "/data/local/nhsystem/kali-arm64"
    private const val KALI_PATH =
        "/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    /** Run a raw command as root (outside the chroot). */
    fun root(cmd: String): String = exec(arrayOf("su", "-c", cmd))

    /** Run a command inside the Kali chroot as root. */
    fun chroot(cmd: String): String {
        val inner = "chroot $CHROOT /usr/bin/env -i " +
            "HOME=/root TERM=xterm LANG=C.UTF-8 PATH=$KALI_PATH " +
            "/bin/bash -c ${singleQuote(cmd)}"
        return exec(arrayOf("su", "-c", inner))
    }

    /** True if we actually have root. */
    fun hasRoot(): Boolean = root("id -u").trim() == "0"

    private fun exec(cmd: Array<String>): String {
        return try {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val out = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
            p.waitFor()
            out
        } catch (e: Exception) {
            "[!] exec error: ${e.message}"
        }
    }

    private fun singleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
