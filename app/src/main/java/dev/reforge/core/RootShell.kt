package dev.reforge.core

import android.util.Base64
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

    // Strips ANSI/VT escape sequences (SGR colors, cursor moves) so tool output
    // reads cleanly in the UI and doesn't waste LLM tokens on control codes.
    private val ansiEscape = Regex("\u001B[\\[\\]][0-9;?]*[ -/]*[@-~]|\u001B[@-Z\\\\-_]")

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

    @Volatile private var currentProc: Process? = null
    @Volatile private var currentTag: String? = null

    /** Kill the currently streaming chroot command (used by Stop). */
    fun cancelProc() {
        try { currentProc?.destroyForcibly() } catch (_: Exception) {}
        // pkill -f takes a regex: quote it so the path stays one argument.
        currentTag?.let { tag ->
            try {
                root("pkill -f ${singleQuote(tag)}")
                root("pkill -f ${singleQuote(tag.removePrefix("/tmp/"))}")
            } catch (_: Exception) {}
        }
        currentProc = null
        currentTag = null
    }

    /**
     * Runs a command in the chroot and streams its output live.
     *  - A PTY (`script`) makes recon tools line-flush in real time.
     *  - `onChunk` is called with batched output (throttled ~150ms) as it arrives.
     *  - `timeout` bounds runtime; Stop (cancelProc) kills the whole job.
     * Returns the full captured output (capped) for the LLM tool result.
     */
    fun chrootStream(cmd: String, timeoutSec: Int = 600, onChunk: (String) -> Unit): String {
        val b64 = Base64.encodeToString(cmd.toByteArray(), Base64.NO_WRAP)
        val tag = "reforge_job_${System.nanoTime()}.sh"
        // Stage the job script inside the chroot's own /tmp: Android's /tmp is
        // shell-owned (unwritable by root), /data/local/tmp isn't mounted into
        // the chroot, and /sdcard is noexec.
        val jobPath = "/tmp/$tag"
        currentTag = jobPath
        // NOTE: keep this free of single quotes. The whole payload is wrapped in
        // single quotes for the outer `bash -c`, so an inner ' — e.g. from a
        // naively nested `script -q -c 'bash x'` — would close the quote early
        // and leave bash running interactively (a raw prompt, no output).
        // Running `script -q -c <prog> <outfile>` with the program path given
        // unquoted is safe here: the path is ours and has no shell metacharacters.
        // Reap job scripts orphaned by a previous Stop/kill before staging this
        // one: when the parent su process is killed the trailing `rm -f` never
        // runs, and old scripts pile up under /tmp.
        val inner = "rm -f /tmp/reforge_job_*.sh; " +
            "echo $b64 | base64 -d > $jobPath; " +
            "timeout $timeoutSec script -q -c bash\\ $jobPath /dev/null 2>&1; " +
            "rm -f $jobPath"
        val full = "chroot $CHROOT /usr/bin/env -i " +
            "HOME=/root TERM=xterm LANG=C.UTF-8 PATH=$KALI_PATH " +
            "/bin/bash -c ${singleQuote(inner)}"
        val sb = StringBuilder()
        return try {
            // `script` allocates a PTY and would otherwise block waiting on the
            // parent's stdin; give the process an empty stdin so it can stream.
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
