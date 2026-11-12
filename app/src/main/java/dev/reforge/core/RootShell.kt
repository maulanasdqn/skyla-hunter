package dev.reforge.core

import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader

object RootShell {
    private const val CHROOT = "/data/local/nhsystem/kali-arm64"
    private const val KALI_PATH =
        "/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

    private val nativeLauncher: Boolean by lazy {
        root("command -v kali-native >/dev/null 2>&1 && echo yes").trim() == "yes"
    }

    private val ansiEscape = Regex("\u001B[\\[\\]][0-9;?]*[ -/]*[@-~]|\u001B[@-Z\\\\-_]")

    fun root(cmd: String): String = exec(arrayOf("su", "-c", cmd))

    fun chroot(cmd: String): String {

        val inner = if (nativeLauncher)
            "KALI_NATIVE_QUIET=1 kali-native /bin/bash -c ${singleQuote(cmd)}"
        else
            "chroot $CHROOT /usr/bin/env -i " +
                "HOME=/root TERM=xterm LANG=C.UTF-8 PATH=$KALI_PATH " +
                "/bin/bash -c ${singleQuote(cmd)}"
        return exec(arrayOf("su", "-c", inner))
    }

    fun hasRoot(): Boolean = root("id -u").trim() == "0"

    @Volatile private var currentProc: Process? = null
    @Volatile private var currentTag: String? = null

    fun cancelProc() {
        try { currentProc?.destroyForcibly() } catch (_: Exception) {}

        currentTag?.let { tag ->
            try {
                root("pkill -f ${singleQuote(tag)}")
                root("pkill -f ${singleQuote(tag.removePrefix("/tmp/"))}")
            } catch (_: Exception) {}
        }
        currentProc = null
        currentTag = null
    }

    fun chrootStream(cmd: String, timeoutSec: Int = 600, onChunk: (String) -> Unit): String {
        val b64 = Base64.encodeToString(cmd.toByteArray(), Base64.NO_WRAP)
        val tag = "reforge_job_${System.nanoTime()}.sh"

        val jobIn = "/tmp/$tag"
        currentTag = jobIn

        val full = if (nativeLauncher) {

            "rm -f $CHROOT/tmp/reforge_job_*.sh; " +
                "echo $b64 | base64 -d > $CHROOT$jobIn; " +
                "KALI_NATIVE_QUIET=1 timeout $timeoutSec " +
                "kali-native script -q -c bash\\ $jobIn /dev/null 2>&1; " +
                "rm -f $CHROOT$jobIn"
        } else {
            val inner = "rm -f /tmp/reforge_job_*.sh; " +
                "echo $b64 | base64 -d > $jobIn; " +
                "timeout $timeoutSec script -q -c bash\\ $jobIn /dev/null 2>&1; " +
                "rm -f $jobIn"
            "chroot $CHROOT /usr/bin/env -i " +
                "HOME=/root TERM=xterm LANG=C.UTF-8 PATH=$KALI_PATH " +
                "/bin/bash -c ${singleQuote(inner)}"
        }
        val sb = StringBuilder()
        return try {

            val proc = ProcessBuilder("su", "-c", full)
                .redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
                .start()
            currentProc = proc
            val reader = proc.inputStream.bufferedReader()
            val pending = StringBuilder()
            var lastFlush = System.currentTimeMillis()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = ansiEscape.replace(line!!.replace("\r", ""), "")
                sb.append(l).append('\n')
                pending.append(l).append('\n')
                val now = System.currentTimeMillis()
                if (now - lastFlush >= 150) {
                    onChunk(pending.toString()); pending.setLength(0); lastFlush = now
                }
            }
            if (pending.isNotEmpty()) onChunk(pending.toString())
            proc.waitFor()
            currentProc = null; currentTag = null
            val out = sb.toString()
            if (out.length > 12000) out.take(12000) + "\n…(truncated)" else out
        } catch (e: Exception) {
            currentProc = null; currentTag = null
            "[!] exec error: ${e.message}\n$sb"
        }
    }

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
