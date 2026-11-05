package dev.reforge.core

/**
 * Compiles Ducky Script into a bash script that writes USB-HID boot-keyboard
 * reports to /dev/hidg0 (the HID gadget NetHunter exposes).
 *
 * The actual compiler now lives in the Rust native core (`skyla_core`) and is
 * reached through UniFFI; this object keeps the original Kotlin API so callers
 * (e.g. HidViewModel) don't change. See rust/src/ducky.rs.
 */
object DuckyScript {
    /** Returns a bash script, or an empty string if nothing typable was found. */
    fun compile(text: String): String = uniffi.skyla_core.compileDucky(text)
}
