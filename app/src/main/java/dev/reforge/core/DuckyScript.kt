package dev.reforge.core

/**
 * Compiles Ducky Script into a bash script that writes USB-HID boot-keyboard
 * reports to /dev/hidg0 (the HID gadget NetHunter exposes). Each report is
 * 8 bytes: [modifier, reserved, key1..key6]; a keypress is a press report
 * followed by an all-zero release report.
 *
 * Supported: REM, DELAY, DEFAULTDELAY/DEFAULT_DELAY, STRING, STRINGLN,
 * ENTER/TAB/SPACE/ESC/arrows/F-keys/etc, and modifier combos
 * (CTRL/ALT/SHIFT/GUI + key), e.g. `GUI r`, `CTRL ALT DELETE`.
 */
object DuckyScript {
    private const val CTRL = 0x01
    private const val SHIFT = 0x02
    private const val ALT = 0x04
    private const val GUI = 0x08

    // char -> (keycode, modifier)
    private val keyForChar: Map<Char, Pair<Int, Int>> by lazy {
        val m = HashMap<Char, Pair<Int, Int>>()
        val lower = "abcdefghijklmnopqrstuvwxyz"
        for (i in lower.indices) {
            m[lower[i]] = (0x04 + i) to 0
            m[lower[i].uppercaseChar()] = (0x04 + i) to SHIFT
        }
        val digits = "1234567890"
        for (i in digits.indices) m[digits[i]] = (0x1e + i) to 0
        val shifted = "!@#\$%^&*()"
        for (i in shifted.indices) m[shifted[i]] = (0x1e + i) to SHIFT
        fun put(base: Char, code: Int, shiftChar: Char? = null) {
            m[base] = code to 0
            if (shiftChar != null) m[shiftChar] = code to SHIFT
        }
        put(' ', 0x2c)
        put('-', 0x2d, '_'); put('=', 0x2e, '+')
        put('[', 0x2f, '{'); put(']', 0x30, '}')
        put('\\', 0x31, '|'); put(';', 0x33, ':')
        put('\'', 0x34, '"'); put('`', 0x35, '~')
        put(',', 0x36, '<'); put('.', 0x37, '>'); put('/', 0x38, '?')
        m['\t'] = 0x2b to 0
        m
    }

    private val named = mapOf(
        "ENTER" to 0x28, "RETURN" to 0x28, "ESC" to 0x29, "ESCAPE" to 0x29,
        "BACKSPACE" to 0x2a, "DELETE" to 0x4c, "DEL" to 0x4c, "TAB" to 0x2b, "SPACE" to 0x2c,
        "UP" to 0x52, "UPARROW" to 0x52, "DOWN" to 0x51, "DOWNARROW" to 0x51,
        "LEFT" to 0x50, "LEFTARROW" to 0x50, "RIGHT" to 0x4f, "RIGHTARROW" to 0x4f,
        "HOME" to 0x4a, "END" to 0x4d, "INSERT" to 0x49, "PAGEUP" to 0x4b, "PAGEDOWN" to 0x4e,
        "CAPSLOCK" to 0x39, "PRINTSCREEN" to 0x46,
        "F1" to 0x3a, "F2" to 0x3b, "F3" to 0x3c, "F4" to 0x3d, "F5" to 0x3e, "F6" to 0x3f,
        "F7" to 0x40, "F8" to 0x41, "F9" to 0x42, "F10" to 0x43, "F11" to 0x44, "F12" to 0x45,
    )
    private val modNames = mapOf(
        "CTRL" to CTRL, "CONTROL" to CTRL, "SHIFT" to SHIFT, "ALT" to ALT,
        "GUI" to GUI, "WINDOWS" to GUI, "WIN" to GUI, "COMMAND" to GUI, "META" to GUI,
    )

    /** Returns a bash script, or an empty string if nothing typable was found. */
    fun compile(text: String): String {
        val sb = StringBuilder()
        sb.append(release()) // make sure no key is stuck before we start
        var defaultDelay = 0
        for (raw in text.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(" ", limit = 2)
            val cmd = parts[0].uppercase()
            val arg = parts.getOrNull(1) ?: ""
            when {
                cmd == "REM" -> {}
                cmd == "DELAY" -> sb.append(sleepMs(arg.trim().toIntOrNull() ?: 0))
                cmd == "DEFAULTDELAY" || cmd == "DEFAULT_DELAY" ->
                    defaultDelay = arg.trim().toIntOrNull() ?: 0
                cmd == "STRING" -> typeString(sb, arg)
                cmd == "STRINGLN" -> { typeString(sb, arg); tap(sb, 0, 0x28) }
                named.containsKey(cmd) -> tap(sb, 0, named[cmd]!!)
                modNames.containsKey(cmd) -> {
                    var mod = 0; var code = 0
                    for (t in line.split(Regex("\\s+"))) {
                        val tu = t.uppercase()
                        when {
                            modNames.containsKey(tu) -> mod = mod or modNames[tu]!!
                            named.containsKey(tu) -> code = named[tu]!!
                            t.length == 1 -> keyForChar[t[0]]?.let { code = it.first; mod = mod or it.second }
                        }
                    }
                    tap(sb, mod, code)
                }
                else -> {}
            }
            if (defaultDelay > 0) sb.append(sleepMs(defaultDelay))
        }
        sb.append(release())
        return sb.toString()
    }

    private fun typeString(sb: StringBuilder, s: String) {
        for (c in s) keyForChar[c]?.let { tap(sb, it.second, it.first) }
    }

    private fun tap(sb: StringBuilder, mod: Int, code: Int) {
        sb.append(printfReport(mod, code))
        sb.append(release())
    }

    private fun printfReport(mod: Int, code: Int): String =
        "printf '\\x%02x\\x00\\x%02x\\x00\\x00\\x00\\x00\\x00' > /dev/hidg0\n".format(mod, code)

    private fun release(): String =
        "printf '\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00' > /dev/hidg0\n"

    private fun sleepMs(ms: Int): String =
        if (ms <= 0) "" else "sleep %.3f\n".format(ms / 1000.0)
}
